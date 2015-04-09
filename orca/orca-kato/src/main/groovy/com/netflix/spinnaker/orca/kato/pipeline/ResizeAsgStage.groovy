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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReference
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.kato.tasks.*
import com.netflix.spinnaker.orca.kato.tasks.ResizeAsgTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ResizeAsgStage extends LinearStage {
  static final String MAYO_CONFIG_TYPE = "resizeAsg"

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  ModifyScalingProcessStage modifyScalingProcessStage

  ResizeAsgStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    if (!stage.syntheticStageOwner) {
      configureTargets(stage)
      []
    } else {
      def step1 = buildStep(stage, "resizeAsg", ResizeAsgTask)
      def step2 = buildStep(stage, "monitorAsg", MonitorKatoTask)
      def step3 = buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)
      def step4 = buildStep(stage, "waitForCapacityMatch", WaitForCapacityMatchTask)
      [step1, step2, step3, step4]
    }
  }

  @CompileDynamic
  private void configureTargets(Stage stage) {
    def targetReferences = targetReferenceSupport.getTargetAsgReferences(stage)
    if (!targetReferences) {
      throw new RuntimeException("Could not determine target ASGs!")
    }

    def optionalConfig = stage.mapTo(OptionalConfiguration)
    Map<String, Map<String, Object>> descriptions = [:]

    for (TargetReference target : targetReferences) {
      def region = target.region
      def asg = target.asg

      def description = new HashMap(stage.context)

      if (descriptions.containsKey(asg.name)) {
        descriptions[asg.name as String].regions.add(region)
        continue
      }
      description.asgName = asg.name
      description.regions = [asg.region]

      def currentMin = Integer.parseInt(asg.asg.minSize.toString())
      def currentDesired = Integer.parseInt(asg.asg.desiredCapacity.toString())
      def currentMax = Integer.parseInt(asg.asg.maxSize.toString())

      int newMin, newDesired, newMax
      if (optionalConfig.scalePct) {
        def factor = optionalConfig.scalePct / 100
        def minDiff = Math.ceil(currentMin * factor)
        def desiredDiff = Math.ceil(currentDesired * factor)
        def maxDiff = Math.ceil(currentMax * factor)
        newMin = currentMin + minDiff
        newDesired = currentDesired + desiredDiff
        newMax = currentMax + maxDiff

        if (optionalConfig.action == ResizeAction.scale_down) {
          newMin = currentMin - minDiff
          newDesired = currentDesired - desiredDiff
          newMax = currentMax - maxDiff
        }
      } else if (optionalConfig.scaleNum) {
        newMin = currentMin + optionalConfig.scaleNum
        newDesired = currentDesired + optionalConfig.scaleNum
        newMax = currentMax + optionalConfig.scaleNum

        if (optionalConfig.action == ResizeAction.scale_down) {
          newMin = Math.min(currentMin - optionalConfig.scaleNum, 0)
          newDesired = Math.min(currentDesired - optionalConfig.scaleNum, 0)
          newMax = Math.min(currentMax - optionalConfig.scaleNum, 0)
        }
      }

      if (newMin && newDesired && newMax) {
        description.capacity = [min: newMin, desired: newDesired, max: newMax]
      }

      descriptions[asg.name as String] = description
    }

    def descriptionList = descriptions.values()
    if (descriptionList.size()) {
      for (description in descriptionList) {
        injectAfter(stage, "resizeAsg", this, description)
      }
    }

    targetReferences.each { targetReference ->
      def context = [
        asgName    : targetReference.asg.name,
        credentials: stage.context.credentials,
        regions    : [targetReference.region]
      ]
      if (targetReference.asg.asg.suspendedProcesses) {
        def suspendedProcesses = targetReference.asg.asg.suspendedProcesses*.processName as List
        def processesToEnable = []
        if (suspendedProcesses.contains("Launch")) {
          processesToEnable << "Launch"
        }
        if (suspendedProcesses.contains("Terminate")) {
          processesToEnable << "Terminate"
        }

        if (processesToEnable) {
          injectBefore(stage, "resumeScalingProcesses", modifyScalingProcessStage, context + [
            action: "resume",
            processes: processesToEnable
          ])
          injectAfter(stage, "suspendScalingProcesses", modifyScalingProcessStage, context + [
            action: "suspend",
            processes: processesToEnable
          ])
        }
      }
    }
  }

  enum ResizeAction {
    scale_up, scale_down
  }

  static class OptionalConfiguration {
    ResizeAction action
    Integer scalePct
    Integer scaleNum
  }
}
