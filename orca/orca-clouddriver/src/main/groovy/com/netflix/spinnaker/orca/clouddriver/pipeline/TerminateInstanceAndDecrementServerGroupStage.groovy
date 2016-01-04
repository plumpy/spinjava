/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline

import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TerminatingInstanceSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.TerminateInstanceAndDecrementServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.WaitForTerminatedInstancesTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class TerminateInstanceAndDecrementServerGroupStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "terminateInstanceAndDecrementServerGroup"

  TerminateInstanceAndDecrementServerGroupStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, PIPELINE_CONFIG_TYPE, TerminateInstanceAndDecrementServerGroupTask),
      buildStep(stage, "monitorTermination", MonitorKatoTask),
      buildStep(stage, "waitForTerminatedInstance", WaitForTerminatedInstancesTask),
    ]
  }
}
