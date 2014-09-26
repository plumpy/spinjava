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

package com.netflix.spinnaker.orca.batch.pipeline

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.monitoring.PipelineMonitor
import com.netflix.spinnaker.orca.pipeline.LinearStage
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Step
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.step.builder.TaskletStepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.core.step.tasklet.TaskletStep
import static java.util.UUID.randomUUID

/**
 * A stub +Stage+ implementation for unit tests that doesn't need to be Spring-wired in order to work. It will
 * just add a single pre-defined +Tasklet+ (probably a mock) to the pipeline.
 */
@CompileStatic
class TestStage extends LinearStage {

  private final List<Tasklet> tasklets = []
  private final PipelineMonitor pipelineMonitor

  TestStage(String name, StepBuilderFactory steps, PipelineMonitor pipelineMonitor) {
    super(name)
    this.steps = steps
    this.pipelineMonitor = pipelineMonitor
  }

  void addTasklet(Tasklet tasklet) {
    tasklets << tasklet
  }

  void leftShift(Tasklet tasklet) {
    addTasklet tasklet
  }

  @Override
  protected List<Step> buildSteps() {
    [buildStep()]
  }

  private TaskletStep buildStep() {
    def listener = new StepExecutionListener() {
      @Override
      void beforeStep(StepExecution stepExecution) {
        pipelineMonitor.beginStage(name)
      }

      @Override
      ExitStatus afterStep(StepExecution stepExecution) {
        pipelineMonitor.endStage(name)
        return stepExecution.exitStatus
      }
    }

    def firstTasklet = tasklets.remove(0)
    def stepBuilder = steps.get(randomUUID().toString())
                           .listener(listener)
                           .tasklet(firstTasklet)
    stepBuilder = (TaskletStepBuilder) tasklets.inject(stepBuilder) { TaskletStepBuilder builder, Tasklet tasklet ->
      builder
        .tasklet(tasklet)
//        .listener(listener)
    }
    stepBuilder.build()
  }
}
