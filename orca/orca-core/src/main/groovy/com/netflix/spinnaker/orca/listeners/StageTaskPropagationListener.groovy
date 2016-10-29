/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.listeners

import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task

@Slf4j
class StageTaskPropagationListener implements StageListener {
  @Override
  void beforeTask(Persister persister, Stage stage, Task task) {
    if (!task.startTime) {
      task.startTime = System.currentTimeMillis()
      task.endTime = null
      task.status = ExecutionStatus.RUNNING
      log.info("Setting task status to ${task.status} (stageId: ${stage.id}, taskId: ${task.id}) [beforeTask]")
      persister.save(stage)
    }
  }

  @Override
  void afterTask(Persister persister, Stage stage, Task task, ExecutionStatus executionStatus, boolean wasSuccessful) {
    task.status = executionStatus
    task.endTime = task.endTime ?: System.currentTimeMillis()
    log.info("Setting task status to ${task.status} (stageId: ${stage.id}, taskId: ${task.id}, taskName: ${task.getName()}) [afterTask]")
    persister.save(stage)
  }
}
