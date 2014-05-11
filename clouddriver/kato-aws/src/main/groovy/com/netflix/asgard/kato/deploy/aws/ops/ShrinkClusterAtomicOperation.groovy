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

package com.netflix.asgard.kato.deploy.aws.ops

import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.netflix.frigga.Names
import com.netflix.asgard.kato.data.task.Task
import com.netflix.asgard.kato.data.task.TaskRepository
import com.netflix.asgard.kato.deploy.aws.description.ShrinkClusterDescription
import com.netflix.asgard.kato.orchestration.AtomicOperation
import org.springframework.web.client.RestTemplate

import static com.netflix.asgard.kato.deploy.aws.StaticAmazonClients.getAutoScaling

class ShrinkClusterAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "SHRINK_CLUSTER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  final ShrinkClusterDescription description
  final RestTemplate rt

  ShrinkClusterAtomicOperation(ShrinkClusterDescription description, RestTemplate rt = new RestTemplate()) {
    this.description = description
    this.rt = rt
  }

  @Override
  Void operate(List _) {
    task.updateStatus BASE_PHASE, "Initializing Cluster Shrinking Operation..."
    for (String region in description.regions) {
      def autoScaling = getAutoScaling(description.credentials, region)

      task.updateStatus BASE_PHASE, "Looking up inactive ASGs in ${region}..."
      List<String> inactiveAsgs = getInactiveAsgs(region)
      for (String inactiveAsg : inactiveAsgs) {
        task.updateStatus BASE_PHASE, "Removing ASG -> ${inactiveAsg}"
        try {
          def request = new DeleteAutoScalingGroupRequest().withAutoScalingGroupName(inactiveAsg)
            .withForceDelete(description.forceDelete)
          autoScaling.deleteAutoScalingGroup(request)
          task.updateStatus BASE_PHASE, "Deleted ASG -> ${inactiveAsg}"
        } catch (IGNORE) {
        }
      }
    }
    task.updateStatus BASE_PHASE, "Finished Shrinking Cluster."
  }

  List<String> getInactiveAsgs(String region) {
    def env = description.credentials.environment

    List<String> asgs = rt.getForEntity("http://entrypoints-v2.${region}.${env}.netflix.net:7001/REST/v2/aws/autoScalingGroups", List).body
    def appAsgs = asgs.findAll {
      def names = Names.parseName(it)
      description.clusterName == names.cluster && description.application == names.app
    }
    appAsgs.findAll { String asgName ->
      try {
        Map asg = rt.getForEntity("http://entrypoints-v2.${region}.${env}.netflix.net:7001/REST/v2/aws/autoScalingGroups/$asgName", Map).body
        !asg.instances
      } catch (IGNORE) {
      }
    }
  }
}
