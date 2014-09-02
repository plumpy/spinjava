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


package com.netflix.spinnaker.kato.deploy.aws.handlers

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.config.KatoAWSConfig.AwsConfigurationProperties
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.deploy.DeployHandler
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.deploy.aws.AutoScalingWorker
import com.netflix.spinnaker.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.deploy.aws.ops.loadbalancer.UpsertAmazonLoadBalancerResult
import com.netflix.spinnaker.kato.deploy.aws.userdata.UserDataProvider
import com.netflix.spinnaker.kato.services.RegionScopedProviderFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class BasicAmazonDeployHandler implements DeployHandler<BasicAmazonDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  List<UserDataProvider> userDataProviders

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  AwsConfigurationProperties awsConfigurationProperties

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicAmazonDeployDescription
  }

  @Override
  DeploymentResult handle(BasicAmazonDeployDescription description, List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing handler..."
    def deploymentResult = new DeploymentResult()
    task.updateStatus BASE_PHASE, "Preparing deployment to ${description.availabilityZones}..."
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      String region = entry.key
      List<String> availabilityZones = entry.value

      // Get the properly typed version of the description's subnetType
      def subnetType = description.subnetType ? AutoScalingWorker.SubnetType.fromString(description.subnetType) : null

      // Get the list of load balancers that were created as part of this conglomerate job to apply to the ASG.
      List<UpsertAmazonLoadBalancerResult.LoadBalancer> suppliedLoadBalancers = (List<UpsertAmazonLoadBalancerResult.LoadBalancer>) priorOutputs.findAll {
        it instanceof UpsertAmazonLoadBalancerResult
      }?.loadBalancers?.getAt(region)

      if (!description.loadBalancers) {
        description.loadBalancers = []
      }
      description.loadBalancers.addAll suppliedLoadBalancers?.name

      def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)

      if (!description.blockDevices) {
        def blockDeviceConfig = awsConfigurationProperties.defaults.instanceClassBlockDevices.find { it.handlesInstanceType(description.instanceType) }
        if (blockDeviceConfig) {
          description.blockDevices = blockDeviceConfig.blockDevices
        }
      }

      def autoScalingWorker = new AutoScalingWorker(
        application: description.application,
        region: region,
        environment: description.credentials.name,
        stack: description.stack,
        freeFormDetails: description.freeFormDetails,
        ami: description.amiName,
        minInstances: description.capacity.min,
        maxInstances: description.capacity.max,
        desiredInstances: description.capacity.desired,
        securityGroups: description.securityGroups,
        iamRole: description.iamRole ?: awsConfigurationProperties.defaults.iamRole,
        keyPair: description.keyPair ?: awsConfigurationProperties.defaults.keyPair,
        ignoreSequence: description.ignoreSequence,
        associatePublicIpAddress: description.associatePublicIpAddress,
        blockDevices: description.blockDevices,
        instanceType: description.instanceType,
        availabilityZones: availabilityZones,
        subnetType: subnetType,
        amazonEC2: amazonEC2,
        autoScaling: autoScaling,
        loadBalancers: description.loadBalancers,
        userDataProviders: userDataProviders,
        securityGroupService: regionScopedProvider.securityGroupService
      )

      def asgName = autoScalingWorker.deploy()

      deploymentResult.serverGroupNames << "${region}:${asgName}".toString()
    }
    deploymentResult
  }
}
