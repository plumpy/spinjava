/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup


import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class CaptureParentInterestingHealthProviderNamesTask implements Task, CloudProviderAware {

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    def parentStage = stage.execution.stages.find { it.id == stage.parentStageId }
    def interestingHealthProviderNames = parentStage?.context?.interestingHealthProviderNames as List<String>

    if (interestingHealthProviderNames != null) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([interestingHealthProviderNames: interestingHealthProviderNames]).build();
    }

    return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
  }
}
