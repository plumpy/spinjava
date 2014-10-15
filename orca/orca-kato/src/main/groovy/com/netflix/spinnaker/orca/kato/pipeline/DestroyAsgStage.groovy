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

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.kato.tasks.*
import com.netflix.spinnaker.orca.pipeline.LinearStage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DestroyAsgStage extends LinearStage {

  public static final String MAYO_CONFIG_TYPE = "destroyAsg"

  @Autowired ResizeAsgStage resizeAsgStage

  DestroyAsgStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps() {
    def resizeSteps = resizeAsgStage.buildSteps()

    def step1 = buildStep("preconfigureResize", PreconfigureDestroyAsgTask)
    def step2 = buildStep("destroyAsg", DestroyAsgTask)
    def step3 = buildStep("monitorAsg", MonitorKatoTask)
    def step4 = buildStep("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    def step5 = buildStep("waitForCapacityMatch", WaitForCapacityMatchTask)
    def step6 = buildStep("sendNotification", NotifyEchoTask)

    [step1, resizeSteps, step2, step3, step4, step5, step6].flatten().toList()
  }
}
