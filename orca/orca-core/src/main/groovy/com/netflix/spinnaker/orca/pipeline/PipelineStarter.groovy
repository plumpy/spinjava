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

package com.netflix.spinnaker.orca.pipeline

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.FlowJobBuilder
import org.springframework.batch.core.job.builder.JobBuilderHelper
import org.springframework.batch.core.job.builder.SimpleJobBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
@CompileStatic
class PipelineStarter {

  @Autowired private ApplicationContext applicationContext
  @Autowired private JobLauncher launcher
  @Autowired private JobBuilderFactory jobs
  @Autowired private StepBuilderFactory steps
  @Autowired private ObjectMapper mapper

  JobExecution start(String config) {
    def context = buildPipelineFrom(config)
    launcher.run(context.job, new JobParameters())
  }

  private PipelineContext buildPipelineFrom(String config) {
    def configMap = mapper.readValue(config, new TypeReference<List<Map>>() {})
    def jobBuilder = jobs.get("orca-job-${UUID.randomUUID()}")
    // TODO: can we get any kind of meaningful identifier from the mayo config?
    def configStep = steps.get("orca-config-step").tasklet({ StepContribution contribution, ChunkContext chunkContext ->
      configMap.each { Map<String, ?> entry ->
        entry.each {
          chunkContext.stepContext.stepExecution.jobExecution.executionContext.put("$entry.type.$it.key", it.value)
        }
      }
      return RepeatStatus.FINISHED
    } as Tasklet).build()
    def context = new PipelineContext(jobBuilder.start(configStep))
    (PipelineContext) configMap.inject(context, this.&buildStageFromConfig)
  }

  @CompileDynamic
  private PipelineContext buildStageFromConfig(PipelineContext context, Map stepConfig) {
    def stageBuilder = applicationContext.getBean("${stepConfig.type}StageBuilder", StageBuilder)
    context.apply(stageBuilder, stepConfig)
  }
}

// TODO: can this class be inlined?
@CompileStatic
class PipelineContext {

  private JobBuilderHelper jobBuilder

  PipelineContext(SimpleJobBuilder jobBuilder) {
    this.jobBuilder = jobBuilder
  }

  @CompileDynamic
  @TypeChecked
  PipelineContext apply(StageBuilder stageBuilder, Map<String, ?> stepConfig) {
    jobBuilder = stageBuilder.build(jobBuilder)
    return this
  }

  Job getJob() {
    // Have to do some horror here as we don't know what type of builder we'll end up with.
    // Two of them have a build method that returns a Job but it's not on a common superclass.
    // If we end up with a plain JobBuilder it implies no steps or flows got added above which I guess is an error.
    switch (jobBuilder) {
      case SimpleJobBuilder:
        return (jobBuilder as SimpleJobBuilder).build()
      case FlowJobBuilder:
        return (jobBuilder as FlowJobBuilder).build()
      default:
        // (╯°□°)╯︵ ┻━┻
        throw new IllegalStateException("Cannot build a Job using a ${jobBuilder.getClass()} - did you add any steps to it?")
    }
  }
}