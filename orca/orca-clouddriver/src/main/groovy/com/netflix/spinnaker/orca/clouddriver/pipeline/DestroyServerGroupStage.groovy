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

package com.netflix.spinnaker.orca.clouddriver.pipeline

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.WaitForDestroyedServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.DestroyServerGroupTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
class DestroyServerGroupStage extends TargetServerGroupLinearStageSupport {
  static final String PIPELINE_CONFIG_TYPE = "destroyServerGroup"

  DestroyServerGroupStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    try {
      composeTargets(stage)
      [
        buildStep(stage, "destroyServerGroup", DestroyServerGroupTask),
        buildStep(stage, "monitorServerGroup", MonitorKatoTask),
        buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
        buildStep(stage, "waitForDestroyedServerGroup", WaitForDestroyedServerGroupTask),
      ]
    } catch (TargetServerGroup.NotFoundException ignored) {
      [buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)]
    }
  }
}
