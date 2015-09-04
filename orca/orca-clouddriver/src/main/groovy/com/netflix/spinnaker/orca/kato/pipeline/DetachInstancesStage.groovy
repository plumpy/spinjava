/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.orca.kato.tasks.DetachInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForTerminatedInstancesTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DetachInstancesStage extends LinearStage {
  public static final String PIPELINE_CONFIG_TYPE = "detachInstances"

  DetachInstancesStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    return [
      buildStep(stage, "detachInstances", DetachInstancesTask),
      buildStep(stage, "monitorDetach", MonitorKatoTask),
      buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
      buildStep(stage, "waitForTerminatedInstances", WaitForTerminatedInstancesTask),
    ]
  }
}
