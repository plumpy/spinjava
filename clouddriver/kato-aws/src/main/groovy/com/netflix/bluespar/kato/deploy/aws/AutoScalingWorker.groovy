/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.bluespar.kato.deploy.aws

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.data.task.TaskRepository
import com.netflix.bluespar.kato.deploy.aws.userdata.UserDataProvider
import com.netflix.frigga.Names
import groovy.transform.InheritConstructors
import org.apache.commons.codec.binary.Base64
import org.joda.time.LocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * A worker class dedicated to the deployment of "applications", following many of Netflix's common AWS conventions.
 *
 * @author Dan Woods
 */
class AutoScalingWorker {
  static final String SUBNET_METADATA_KEY = "immutable_metadata"
  private static final String SUBNET_PURPOSE_TYPE = "ec2"
  private static final String AWS_PHASE = "AWS_DEPLOY"

  enum SubnetType {
    INTERNAL("internal"), EXTERNAL("external")

    String type

    SubnetType(String type) {
      this.type = type
    }

    static SubnetType fromString(String type) {
      for (t in values()) {
        if (t.type == type) {
          return t
        }
      }
      throw new SubnetTypeNotFoundException()
    }
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final ObjectMapper objectMapper = new ObjectMapper()

  private String application
  private String region
  private String environment
  private String stack
  private String ami
  private String instanceType
  private String iamRole
  private SubnetType subnetType
  private List<String> loadBalancers
  private List<String> securityGroups
  private List<String> availabilityZones
  private AmazonEC2 amazonEC2
  private AmazonAutoScaling autoScaling

  private int minInstances
  private int maxInstances
  private int desiredInstances

  private List<UserDataProvider> userDataProviders = []

  AutoScalingWorker() {

  }

  /**
   * Initiates the activity of deployment. This will involve:
   *  <ol>
   *    <li>Lookup or create if not found, a security group with a name that matches the supplied "application";</li>
   *    <li>Looking up security group ids for the names provided as "securityGroups";</li>
   *    <li>Look up an ancestor ASG based on Netflix naming conventions, and bring its security groups to the new ASG;</li>
   *    <li>Retrieve user data from all available {@link UserDataProvider}s;</li>
   *    <li>Create the ASG's Launch Configuration with User Data and Security Groups;</li>
   *    <li>Create a new ASG in the subnets found from the optionally supplied subnetType.</li>
   *  </ol>
   *
   * @return the name of the newly deployed ASG
   */
  String deploy() {
    task.updateStatus AWS_PHASE, "Beginning Amazon deployment."

    task.updateStatus AWS_PHASE, "Checking for security package."
    String applicationSecurityGroup = getSecurityGroupForApplication()
    if (!applicationSecurityGroup) {
      applicationSecurityGroup = createSecurityGroup()
    }

    task.updateStatus AWS_PHASE, "Looking up security groups..."
    if (securityGroups) {
      securityGroups = getSecurityGroupIds(securityGroups as String[])
    } else {
      securityGroups = []
    }

    task.updateStatus AWS_PHASE, "Beginning ASG deployment."
    securityGroups << applicationSecurityGroup
    def ancestorAsg = ancestorAsg
    Integer nextSequence
    if (ancestorAsg) {
      task.updateStatus AWS_PHASE, "Found ancestor ASG: parsing details."
      Names ancestorNames = Names.parseName(ancestorAsg.autoScalingGroupName as String)
      nextSequence = ancestorNames.sequence + 1
    } else {
      nextSequence = 0
    }

    String asgName = getAutoScalingGroupName(nextSequence)
    String launchConfigName = getLaunchConfigurationName(nextSequence)

    def userData = getUserData(asgName, launchConfigName)
    task.updateStatus AWS_PHASE, "Building launch configuration for new ASG."
    createLaunchConfiguration(launchConfigName, userData, securityGroups?.unique())
    task.updateStatus AWS_PHASE, "Deploying ASG."

    createAutoScalingGroup(asgName, launchConfigName)
  }

  /**
   * Builds the launch configuration name for this deployment following Netflix naming conventions.
   *
   * @param nextSequence
   * @return
   */
  String getLaunchConfigurationName(Integer nextSequence) {
    def nowTime = new LocalDateTime().toString("MMddYYYYHHmmss")
    "${getAutoScalingGroupName(nextSequence)}-${nowTime}"
  }

  /**
   * This is an obscure rule that Subnets are tagged at Amazon with a data structure, which defines their purpose and
   * what type of resources (elb or ec2) are able to make use of them. We also need to ensure that the Subnet IDs that
   * we provide back are able to be deployed to based off of the supplied availability zones.
   *
   * @return list of subnet ids applicable to this deployment.
   */
  List<String> getSubnetIds() {
    def response = amazonEC2.describeSubnets()
    def subnets = []
    response.subnets.each { subnet ->
      def metadataJson = subnet.tags.find { it.key == SUBNET_METADATA_KEY }?.value
      if (metadataJson) {
        def metadata = objectMapper.readValue metadataJson, Map
        if (metadata.containsKey("purpose") && metadata.purpose == subnetType?.type
          && metadata.target == SUBNET_PURPOSE_TYPE) {
          subnets << subnet.subnetId
        }
      }
    }
    subnets
  }

  /**
   * This will return the VPC id for the subnet type provided, and derived from Netflix rules. This may be used to
   * ensure that a newly created security group is available in the same VPC as our instances.
   *
   * @return id of the vpc
   */
  String getVpcForSubnetType() {
    def response = amazonEC2.describeSubnets()
    for (subnet in response.subnets) {
      def metadataJson = subnet.tags.find { it.key == SUBNET_METADATA_KEY }?.value
      if (metadataJson) {
        def metadata = objectMapper.readValue metadataJson, Map
        if (metadata.containsKey("purpose") && metadata.purpose == subnetType?.type
          && metadata.target == SUBNET_PURPOSE_TYPE) {
          return subnet.vpcId
        }
      }
    }
    null
  }

  /**
   * Will lookup an existing ASG for this deployable, based off of Netflix ASG naming conventions.
   *
   * @return map depicting the ASG data structure.
   */
  AutoScalingGroup getAncestorAsg() {
    def request = new DescribeAutoScalingGroupsRequest()
    def result = autoScaling.describeAutoScalingGroups(request)
    List<AutoScalingGroup> asgs = []
    while (true) {
      asgs.addAll result.autoScalingGroups
      if (result.nextToken) {
        result = autoScaling.describeAutoScalingGroups(request.withNextToken(result.nextToken))
      } else {
        break
      }
    }
    asgs.findAll { AutoScalingGroup asg ->
      def names = Names.parseName(asg.autoScalingGroupName)
      names.sequence >= 0 && application == names.app
    }?.max( { a, b -> a.autoScalingGroupName <=> b.autoScalingGroupName } ) ?: null
  }

  /**
   * Find a security group that matches the name of this deployable.
   *
   * @return
   */
  String getSecurityGroupForApplication() {
    try {
      getSecurityGroupIds([application] as String[])?.getAt(0)
    } catch (SecurityGroupNotFoundException IGNORE) {
      null
    }
  }

  /**
   * Find security group ids for an array of provided security group names
   *
   * @param names
   * @return list of group ids that correspond to the provided security group names
   */
  List<String> getSecurityGroupIds(String... names) {
    DescribeSecurityGroupsResult result = amazonEC2.describeSecurityGroups()
    def securityGroups = result.securityGroups.findAll { names.contains(it.groupName) }.collectEntries {
      [(it.groupName): it.groupId]
    }
    if (names.minus(securityGroups.keySet()).size() > 0) {
      throw new SecurityGroupNotFoundException()
    }
    securityGroups.values() as List
  }

  /**
   * Create a security group for this this deployable. Security Group name will equal the deployable's
   * (ie. "application") name.
   *
   * @return group id of the security group
   */
  String createSecurityGroup() {
    CreateSecurityGroupRequest request = new CreateSecurityGroupRequest(application, "Security Group for $application")
    if (subnetType) {
      request.withVpcId(vpcForSubnetType)
    }
    CreateSecurityGroupResult result = amazonEC2.createSecurityGroup(request)
    result.groupId
  }

  /**
   * Creates a launch configuration from this deployment with supplied name, userdata, and security groups.
   *
   * @param name
   * @param userData
   * @param securityGroups
   * @return name of the launch configuration
   */
  String createLaunchConfiguration(String name, String userData, List<String> securityGroups) {
    CreateLaunchConfigurationRequest request = new CreateLaunchConfigurationRequest()
      .withImageId(ami)
      .withIamInstanceProfile(iamRole)
      .withInstanceMonitoring(new com.amazonaws.services.autoscaling.model.InstanceMonitoring().withEnabled(true))
      .withLaunchConfigurationName(name)
      .withUserData(userData)
      .withInstanceType(instanceType)
      .withSecurityGroups(securityGroups)
      .withKeyName("nf-test-keypair-a")
    autoScaling.createLaunchConfiguration(request)

    name
  }

  /**
   * Asgard's convention for naming AutoScaling Groups.
   *
   * @param sequence
   * @return
   */
  String getAutoScalingGroupName(Integer sequence) {
    def pushVersion = String.format("v%03d", sequence)
    "${clusterName}-${pushVersion}"
  }

  /**
   * Asgard's convention for naming a Cluster. A cluster doesn't really exist, but is derived from the application name and the stack.
   *
   * @return the name of the cluster to be deployed to
   */
  String getClusterName() {
    "${application}${stack ? '-'+stack?.replaceAll("$application-", "") : ''}"
  }

  /**
   * Deploys a new ASG with as much data collected as possible.
   *
   * @param asgName
   * @param launchConfigurationName
   * @return
   */
  String createAutoScalingGroup(String asgName, String launchConfigurationName) {
    CreateAutoScalingGroupRequest request = new CreateAutoScalingGroupRequest()
      .withAutoScalingGroupName(asgName)
      .withLaunchConfigurationName(launchConfigurationName)
      .withMinSize(minInstances)
      .withMaxSize(maxInstances)
      .withDesiredCapacity(desiredInstances)
      .withDefaultCooldown(10)
      .withHealthCheckGracePeriod(600)
      .withLoadBalancerNames(loadBalancers)

    // Favor subnetIds over availability zones
    def subnetIds = subnetIds?.join(',')
    if (subnetIds) {
      request.withVPCZoneIdentifier(subnetIds)
    } else {
      request.withAvailabilityZones(availabilityZones)
    }

    autoScaling.createAutoScalingGroup(request)

    asgName
  }

  /**
   * Traverses all supplied instances of {@link UserDataProvider} and concatenates their results to a Base64-encoded
   * string.
   *
   * @param asgName
   * @param launchConfigName
   * @return base64-encoded String
   */
  String getUserData(String asgName, String launchConfigName) {
    def data = userDataProviders.collect { udp ->
      udp.getUserData(asgName, launchConfigName, region, environment)
    }?.join("\n")
    data ? new String(Base64.encodeBase64(data?.bytes)) : null
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "could not find all supplied security groups!")
  @InheritConstructors
  static class SecurityGroupNotFoundException extends RuntimeException {}

  @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "subnet not found for the provided subnetType")
  @InheritConstructors
  static class SubnetTypeNotFoundException extends RuntimeException {}

}
