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

package com.netflix.spinnaker.orca.batch

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import com.netflix.spinnaker.orca.pipeline.Pipeline
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.core.step.tasklet.TaskletStep
import org.springframework.batch.repeat.RepeatStatus
import static org.springframework.batch.repeat.RepeatStatus.FINISHED

@CompileStatic
@TupleConstructor(includeFields = true)
class PipelineInitializerTasklet implements Tasklet {

  static TaskletStep initializationStep(StepBuilderFactory steps, Pipeline pipeline) {
    steps.get("orca-init-step")
         .tasklet(new PipelineInitializerTasklet(pipeline))
         .build()
  }

  public static final String PIPELINE_CONTEXT_KEY = "pipeline"

  private final Pipeline pipeline

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    chunkContext.stepContext.stepExecution.jobExecution.with {
      executionContext.put(PIPELINE_CONTEXT_KEY, pipeline)
    }
    return FINISHED
  }
}
