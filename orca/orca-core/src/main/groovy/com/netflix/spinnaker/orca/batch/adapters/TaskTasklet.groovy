/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.adapters

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.BatchStepStatus
import com.netflix.spinnaker.orca.pipeline.PipelineStage
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus

@CompileStatic
class TaskTasklet implements Tasklet {

  private final Task task

  TaskTasklet(Task task) {
    this.task = task
  }

  Class<? extends Task> getTaskType() {
    task.getClass()
  }

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    def stage = currentStage(chunkContext)

    def result = task.execute(stage)

    if (result.status == PipelineStatus.TERMINAL) {
      chunkContext.stepContext.stepExecution.with {
        setTerminateOnly()
        executionContext.put("orcaTaskStatus", result.status)
        exitStatus = ExitStatus.FAILED
      }
    }

    stage.updateContext(result.outputs)

    def batchStepStatus = BatchStepStatus.mapResult(result)
    chunkContext.stepContext.stepExecution.executionContext.put("orcaTaskStatus", result.status)
    contribution.exitStatus = batchStepStatus.exitStatus

    return batchStepStatus.repeatStatus
  }

  private PipelineStage currentStage(ChunkContext chunkContext) {
    (PipelineStage) chunkContext.stepContext.stepExecution.jobExecution
                                .executionContext.get(stageName(chunkContext))
  }

  private static String stageName(ChunkContext chunkContext) {
    chunkContext.stepContext.stepName.tokenize(".").first()
  }
}

