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


package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.deploy.aws.AutoScalingWorker
import com.netflix.spinnaker.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.deploy.aws.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CopyLastAsgAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "COPY_LAST_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  BasicAmazonDeployHandler basicAmazonDeployHandler

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  ObjectMapper objectMapper

  final BasicAmazonDeployDescription description

  CopyLastAsgAtomicOperation(BasicAmazonDeployDescription description) {
    this.description = description
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Copy Last ASG Operation..."

    DeploymentResult result = new DeploymentResult()
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      def region = entry.key
      task.updateStatus BASE_PHASE, "Looking up last ASG in ${region} for ${description.application}-${description.stack}."

      def ancestorAsg = getAncestorAsg(region)
      def ancestorLaunchConfiguration = getLaunchConfiguration(region, ancestorAsg.launchConfigurationName)
      BasicAmazonDeployDescription newDescription = description.clone()

      if (ancestorAsg.VPCZoneIdentifier) {
        task.updateStatus BASE_PHASE, " > Looking up subnet type..."
        newDescription.subnetType = getPurposeForSubnet(region, ancestorAsg.VPCZoneIdentifier.tokenize(',').getAt(0))
        task.updateStatus BASE_PHASE, " > Found: ${newDescription.subnetType}."
      }

      newDescription.iamRole = description.iamRole ?: ancestorLaunchConfiguration.iamInstanceProfile
      newDescription.amiName = description.amiName ?: ancestorLaunchConfiguration.imageId
      newDescription.availabilityZones = description.availabilityZones ?: [(region): ancestorAsg.availabilityZones]
      newDescription.instanceType = description.instanceType ?: ancestorLaunchConfiguration.instanceType
      newDescription.loadBalancers = description.loadBalancers ?: ancestorAsg.loadBalancerNames
      newDescription.securityGroups = getSecurityGroupNamesForIds(region, description.securityGroups ?: ancestorLaunchConfiguration.securityGroups)
      newDescription.capacity.min = ancestorAsg.minSize
      newDescription.capacity.max = ancestorAsg.maxSize
      newDescription.capacity.desired = ancestorAsg.desiredCapacity

      task.updateStatus BASE_PHASE, "Initiating deployment."
      def thisResult = basicAmazonDeployHandler.handle(newDescription, priorOutputs)
      result.serverGroupNames.addAll(thisResult.serverGroupNames)
      result.messages.addAll(thisResult.messages)
      task.updateStatus BASE_PHASE, "Deployment complete in $region. New ASGs = ${result.serverGroupNames}"
    }
    task.updateStatus BASE_PHASE, "Finished copying last ASG for ${description.application}-${description.stack}. New ASGs = ${result.serverGroupNames}."

    result
  }

  AutoScalingGroup getAncestorAsg(String region) {
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
    def autoScalingWorker = new AutoScalingWorker(application: description.application, autoScaling: autoScaling, region: region,
      environment: description.credentials.environment)
    autoScalingWorker.ancestorAsg
  }

  LaunchConfiguration getLaunchConfiguration(String region, String launchConfigurationName) {
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
    def result = autoScaling.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest().withLaunchConfigurationNames(launchConfigurationName))
    result.launchConfigurations?.getAt(0)
  }

  String getPurposeForSubnet(String region, String subnetId) {
    def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)
    def result = amazonEC2.describeSubnets(new DescribeSubnetsRequest().withSubnetIds(subnetId))
    def json = result.subnets?.getAt(0)?.tags?.find { it.key == AutoScalingWorker.SUBNET_METADATA_KEY }?.value
    def metadata = objectMapper.readValue(json, Map)
    (metadata && metadata.purpose) ? metadata.purpose : null
  }

  List<String> getSecurityGroupNamesForIds(String region, List<String> ids) {
    def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)
    def result = amazonEC2.describeSecurityGroups(new DescribeSecurityGroupsRequest().withGroupIds(ids))
    result.securityGroups*.groupName
  }
}
