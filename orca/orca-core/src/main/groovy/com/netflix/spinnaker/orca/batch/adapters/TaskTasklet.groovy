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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.BatchStepStatus
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus

@CompileStatic
class TaskTasklet implements Tasklet {

  private final Task task
  private final ExecutionRepository executionRepository
  private final List<ExceptionHandler> exceptionHandlers

  TaskTasklet(Task task, ExecutionRepository executionRepository, List<ExceptionHandler> exceptionHandlers) {
    this.task = task
    this.executionRepository = executionRepository
    this.exceptionHandlers = exceptionHandlers
  }

  Class<? extends Task> getTaskType() {
    task.getClass()
  }

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    def stage = currentStage(chunkContext)

    try {
      if (stage.execution.canceled) {
        setStopStatus(chunkContext, ExitStatus.STOPPED, ExecutionStatus.CANCELED)
        return cancel(stage)
      } else {
        def result = executeTask(stage, chunkContext)
        logResult(result, stage, chunkContext)

        // we should reload the execution now, in case it has been affected
        // by a parallel process
        long scheduledTime =  stage.scheduledTime
        stage = currentStage(chunkContext)
        // Setting the scheduledTime if it has been set by the task
        stage.scheduledTime = scheduledTime

        if (result.status == ExecutionStatus.TERMINAL) {
          setStopStatus(chunkContext, ExitStatus.FAILED, result.status)
        }

        def contextResults = new HashMap(result.outputs)
        if (result.status.complete) {
          contextResults.put 'batch.task.id.' + taskName(chunkContext), chunkContext.stepContext.stepExecution.id
        }

        stage.context.putAll contextResults

        def batchStepStatus = BatchStepStatus.mapResult(result)
        chunkContext.stepContext.stepExecution.executionContext.put("orcaTaskStatus", result.status)
        if (result.status == ExecutionStatus.SUSPENDED) {
          chunkContext.stepContext.stepExecution.status = batchStepStatus.batchStatus
          chunkContext.stepContext.stepExecution.jobExecution.status = batchStepStatus.batchStatus
        }
        contribution.exitStatus = batchStepStatus.exitStatus
        stage.endTime = !batchStepStatus.repeatStatus.continuable ? System.currentTimeMillis() : null

        return batchStepStatus.repeatStatus
      }
    } finally {
      save(stage.execution)
    }
  }

  private RepeatStatus cancel(Stage stage) {
    stage.status = ExecutionStatus.CANCELED
    stage.endTime = System.currentTimeMillis()
    stage.tasks.findAll { !it.status.complete }.each { it.status = ExecutionStatus.CANCELED }
    return BatchStepStatus.mapResult(new DefaultTaskResult(ExecutionStatus.CANCELED)).repeatStatus
  }

  private void save(Execution execution) {
    if (execution instanceof Orchestration) {
      executionRepository.store(execution)
    } else if (execution instanceof Pipeline) {
      executionRepository.store(execution)
    }
  }

  private static void setStopStatus(ChunkContext chunkContext, ExitStatus exitStatus, ExecutionStatus executionStatus) {
    chunkContext.stepContext.stepExecution.with {
      setTerminateOnly()
      executionContext.put("orcaTaskStatus", executionStatus)
      it.exitStatus = exitStatus
    }
  }

  protected TaskResult doExecuteTask(Stage stage, ChunkContext chunkContext) {
    return task.execute(stage)
  }

  private TaskResult executeTask(Stage stage, ChunkContext chunkContext) {
    try {
      return doExecuteTask(stage.asImmutable(), chunkContext)
    } catch (RuntimeException e) {
      def exceptionHandler = exceptionHandlers.find { it.handles(e) }
      if (!exceptionHandler) {
        throw e
      }

      def taskName = (!stage.tasks.isEmpty() ? stage.tasks[-1].name : null) as String
      return new DefaultTaskResult(ExecutionStatus.TERMINAL, [
        "exception": exceptionHandler.handle(taskName, e)
      ])
    }
  }

  private Execution currentExecution(ChunkContext chunkContext) {
    if (chunkContext.stepContext.jobParameters.containsKey("pipeline")) {
      def id = chunkContext.stepContext.jobParameters.pipeline as String
      executionRepository.retrievePipeline(id)
    } else {
      def id = chunkContext.stepContext.jobParameters.orchestration as String
      executionRepository.retrieveOrchestration(id)
    }
  }

  private Stage currentStage(ChunkContext chunkContext) {
    def execution = currentExecution(chunkContext)
    execution.stages.find { it.id == stageId(chunkContext) }
  }

  private static String stageId(ChunkContext chunkContext) {
    chunkContext.stepContext.stepName.tokenize(".").first()
  }

  private static String taskName(ChunkContext chunkContext) {
    chunkContext.stepContext.stepName.tokenize(".").getAt(2) ?: "Unknown"
  }

  private void logResult(TaskResult result, Stage stage, ChunkContext chunkContext) {
    def taskLogger = LoggerFactory.getLogger(task.class)
    if (result.status.complete || taskLogger.isDebugEnabled()) {
      def message = "${stage.execution.class.simpleName}:${stage.execution.id} ${taskName(chunkContext)} ${result.status} -- Batch step id: ${chunkContext.stepContext.stepExecution.id},  Task Outputs: ${result.outputs},  Stage Context: ${stage.context}"
      if (result.status.complete) {
        taskLogger.info message
      } else {
        taskLogger.debug message
      }
    }
  }
}

