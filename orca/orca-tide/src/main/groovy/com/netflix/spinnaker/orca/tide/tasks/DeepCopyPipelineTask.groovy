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

package com.netflix.spinnaker.orca.tide.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.tide.TideService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DeepCopyPipelineTask implements Task {

  @Autowired
  TideService tideService

  @Override
  TaskResult execute(Stage stage) {
    Map<String, String> source = stage.context.source
    Map<String, String> target = stage.context.target

    def settings = stage.mapTo(DeepCopySettings)

    Map pipelineVpcMigrateDefinition = [
        sourceVpcName: source.vpcName,
        targetVpcName: target.vpcName
    ]
    def taskId = tideService.deepCopyPipeline(source.pipelineId, settings.allowIngressFromClassic, settings.dryRun,
      target.subnetType,
      pipelineVpcMigrateDefinition)

    def outputs = [
        "tide.task.id": taskId
    ]

    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)

  }

  static class DeepCopySettings {
    boolean dryRun = false
    boolean allowIngressFromClassic = true
  }
}
