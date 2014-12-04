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

package com.netflix.spinnaker.kato.aws.deploy.ops
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.frigga.Names
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.kato.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.RestTemplate

abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {
  private static final long THROTTLE_MS = 150

  abstract boolean isDisable()

  abstract String getPhaseName()

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  RestTemplate restTemplate

  @Autowired
  AmazonClientProvider amazonClientProvider

  EnableDisableAsgDescription description

  AbstractEnableDisableAtomicOperation(EnableDisableAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'Disable' : 'Enable'
    String presentParticipling = disable ? 'Disabling' : 'Enabling'
    task.updateStatus phaseName, "Initializing ${verb} ASG operation for '$description.asgName'..."
    boolean failures = false
    for (region in description.regions) {
      try {
        def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)
        def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, region)

        def asgService = regionScopedProvider.asgService
        def asg = asgService.getAutoScalingGroup(description.asgName)
        if (!asg) {
          task.updateStatus phaseName, "No ASG named '$description.asgName' found in $region"
          continue
        }
        task.updateStatus phaseName, "${presentParticipling} ASG '$description.asgName' in $region..."
        if (disable) {
          asgService.suspendProcesses(description.asgName, AutoScalingProcessType.getDisableProcesses())
        } else {
          asgService.resumeProcesses(description.asgName, AutoScalingProcessType.getDisableProcesses())
        }

        if (disable) {
          task.updateStatus phaseName, "Deregistering instances from Load Balancers..."
          changeRegistrationOfInstancesWithLoadBalancer(asg.loadBalancerNames, asg.instances*.instanceId) { String loadBalancerName, List<Instance> instances ->
            loadBalancing.deregisterInstancesFromLoadBalancer(new DeregisterInstancesFromLoadBalancerRequest(loadBalancerName, instances))
          }
        } else {
          task.updateStatus phaseName, "Registering instances with Load Balancers..."
          changeRegistrationOfInstancesWithLoadBalancer(asg.loadBalancerNames, asg.instances*.instanceId) { String loadBalancerName, List<Instance> instances ->
            loadBalancing.registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(loadBalancerName, instances))
          }
        }

        if (description.credentials.discoveryEnabled) {
          def status = disable ? DiscoveryStatus.Disable : DiscoveryStatus.Enable
          task.updateStatus phaseName, "Marking ASG $description.asgName as $status with Discovery"
          doDiscoveryStatusCall region, status, asg.autoScalingGroupName, asg.instances*.instanceId
        }
        task.updateStatus phaseName, "Finished ${presentParticipling} ASG $description.asgName."
      } catch (e) {
        task.updateStatus phaseName, "Could not ${verb} ASG '$description.asgName' in region $region! Failure Type: ${e.class.simpleName}; Message: ${e.message}"
        failures = true
      }
    }
    if (failures) {
      task.fail()
    }
    null
  }

  private void doDiscoveryStatusCall(String region, DiscoveryStatus discoveryStatus, String asgName, List<String> instanceIds) {
    if (!description.credentials.discoveryEnabled) {
      throw new DiscoveryNotConfiguredException()
    }
    def names = Names.parseName(asgName)
    if (!names.app) {
      task.updateStatus phaseName, "Could not derive application name from ASG name and unable to ${discoveryStatus.name().toLowerCase()} in Eureka!"
    } else {
      instanceIds.eachWithIndex { instanceId, index ->
        if (index > 0) {
          sleep THROTTLE_MS
        }
        task.updateStatus phaseName, "Attempting to ${discoveryStatus.name().toLowerCase()} instance '$instanceId'."
        def discovery = String.format(description.credentials.discovery, region)
        restTemplate.put("$discovery/v2/apps/$names.app/$instanceId/status?value=$discoveryStatus.value", [:])
      }
    }
  }

  private static void changeRegistrationOfInstancesWithLoadBalancer(Collection<String> loadBalancerNames, Collection<String> instanceIds, Closure actOnInstancesAndLoadBalancer) {
    if (instanceIds && loadBalancerNames) {
      def instances = instanceIds.collect { new Instance(instanceId: it) }
      loadBalancerNames.eachWithIndex { String loadBalancerName, int index ->
        if (index > 0) {
          sleep THROTTLE_MS
        }
        actOnInstancesAndLoadBalancer(loadBalancerName, instances)
      }
    }
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  enum DiscoveryStatus {
    Enable('UP'),
    Disable('OUT_OF_SERVICE')

    String value

    DiscoveryStatus(String value) {
      this.value = value
    }
  }

  @InheritConstructors
  static class DiscoveryNotConfiguredException extends RuntimeException {}
}
