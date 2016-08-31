/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.instance

import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.RebootInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class RebootInstancesStage extends LinearStage {
  public static final String PIPELINE_CONFIG_TYPE = "rebootInstances"

  RebootInstancesStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    def step0 = buildStep(stage, "determineHealthProviders", DetermineHealthProvidersTask)
    def step1 = buildStep(stage, "rebootInstances", RebootInstancesTask)
    def step2 = buildStep(stage, "monitorReboot", MonitorKatoTask)
    def step3 = buildStep(stage, "waitForDownInstances", WaitForDownInstanceHealthTask)
    def step4 = buildStep(stage, "waitForUpInstances", WaitForUpInstanceHealthTask)
    [step0, step1, step2, step3, step4]
  }
}
