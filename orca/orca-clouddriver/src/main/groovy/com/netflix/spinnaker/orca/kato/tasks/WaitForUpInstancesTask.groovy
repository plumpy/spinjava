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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@CompileStatic
@Slf4j
class WaitForUpInstancesTask extends AbstractWaitingForInstancesTask {

  static final int MIN_ZERO_INSTANCE_RETRY_COUNT = 12

  static boolean allInstancesMatch(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    if (!(serverGroup?.capacity)) {
      return false
    }
    int targetDesiredSize = calculateTargetDesiredSize(stage, serverGroup)

    if (targetDesiredSize == 0 && stage.context.capacitySnapshot) {
      // if we've seen a non-zero value before, but we are seeing a target size of zero now, assume
      // it's a transient issue with edda unless we see it repeatedly
      Map snapshot = stage.context.capacitySnapshot as Map
      Integer snapshotDesiredCapacity = snapshot.desiredCapacity as Integer
      if (snapshotDesiredCapacity != 0) {
        Integer seenCount = stage.context.zeroDesiredCapacityCount as Integer
        return seenCount >= MIN_ZERO_INSTANCE_RETRY_COUNT
      }
    }

    if (targetDesiredSize > instances.size()) {
      return false
    }

    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      log.info("${serverGroup.name}: Empty health providers supplied; considering it healthy")
      return true
    }

    def healthyCount = instances.count { Map instance ->
      def healths = interestingHealthProviderNames ? instance.health.findAll { Map health ->
        health.type in interestingHealthProviderNames
      } : instance.health
      boolean someAreUp = healths.any { Map health -> health.state == 'Up' }
      if (interestingHealthProviderNames?.contains("Amazon")) {
        // given that Amazon health never reports as 'Up' (only 'Unknown') we can only verify it isn't 'Down'.
        someAreUp = someAreUp || healths.any { Map health -> health.type == 'Amazon' && health.state != 'Down' }
      }
      boolean noneAreDown = !healths.any { Map health -> health.state == 'Down' }
      someAreUp && noneAreDown
    }
    log.info("${serverGroup.name}: Instances up check - healthy: $healthyCount, target: $targetDesiredSize")
    return healthyCount >= targetDesiredSize
  }

  private static int calculateTargetDesiredSize(Stage stage, Map serverGroup) {
    // favor using configured target capacity whenever available (rather than in-progress asg's desiredCapacity)
    Map capacity = (Map) serverGroup.capacity
    Integer targetDesiredSize = capacity.desired as Integer

    if (stage.context.capacitySnapshot) {
      Integer snapshotCapacity = ((Map) stage.context.capacitySnapshot).desiredCapacity as Integer
      // if the ASG is being actively scaled down, this operation might never complete,
      // so take the min of the latest capacity from the ASG and the snapshot
      log.info("${serverGroup.name}: Calculating target desired size from snapshot (${snapshotCapacity}) and ASG (${targetDesiredSize})")
      targetDesiredSize = Math.min(targetDesiredSize, snapshotCapacity)
    }

    if (stage.context.targetHealthyDeployPercentage != null) {
      Integer percentage = (Integer) stage.context.targetHealthyDeployPercentage
      if (percentage < 0 || percentage > 100) {
        throw new NumberFormatException("targetHealthyDeployPercentage must be an integer between 0 and 100")
      }
      targetDesiredSize = Math.ceil(percentage * targetDesiredSize / 100D) as Integer
      log.info("${serverGroup.name}: Calculating target desired size based on configured percentage (${percentage}) as ${targetDesiredSize} instances")
    }
    log.info("${serverGroup.name}: Target desired size is ${targetDesiredSize}")
    targetDesiredSize
  }

  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    allInstancesMatch(stage, serverGroup, instances, interestingHealthProviderNames)
  }

}
