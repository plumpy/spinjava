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
import groovy.transform.Immutable
import org.springframework.stereotype.Component

@Component
@CompileStatic
class WaitForUpInstancesTask extends AbstractWaitingForInstancesTask {

  static boolean allInstancesMatch(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    int targetDesiredSize = calculateTargetDesiredSize(stage, serverGroup)
    if (targetDesiredSize > instances.size()) {
      return false
    }

    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
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

    return healthyCount >= targetDesiredSize
  }

  private static int calculateTargetDesiredSize(Stage stage, Map serverGroup) {
    // favor using configured target capacity whenever available (rather than in-progress asg's desiredCapacity)
    CapacityConfig capacityConfig = stage.context.capacity ? stage.mapTo("/capacity", CapacityConfig) : null
    Map source = stage.context.source as Map
    Boolean useSourceCapacity = source?.useSourceCapacity as Boolean
    Map asg = (Map) serverGroup?.asg ?: [:]
    Integer targetDesiredSize = (capacityConfig?.desired != null && !useSourceCapacity) ?
      capacityConfig.desired :
      stage.context.capacitySnapshot ?
        ((Map) stage.context.capacitySnapshot).desiredCapacity as Integer :
        asg.desiredCapacity as Integer
    if (stage.context.targetHealthyDeployPercentage != null) {
      targetDesiredSize = Math.ceil((stage.context.targetHealthyDeployPercentage as Double) * targetDesiredSize / 100) as Integer
    }
    targetDesiredSize
  }

  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    allInstancesMatch(stage, serverGroup, instances, interestingHealthProviderNames)
  }

  @Immutable
  static class CapacityConfig {
    Integer min
    Integer max
    Integer desired
  }
}
