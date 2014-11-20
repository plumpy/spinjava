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

package com.netflix.spinnaker.orca.pipeline

import javax.annotation.PostConstruct
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.batch.StageBuilder
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

abstract class AbstractOrchestrationInitiator<T> {

  @Autowired protected ApplicationContext applicationContext
  @Autowired protected JobLauncher launcher
  @Autowired protected JobBuilderFactory jobs
  @Autowired protected StepBuilderFactory steps
  @Autowired protected ObjectMapper mapper
  @Autowired protected PipelineStore pipelineStore

  protected final Map<String, StageBuilder> stages = [:]

  @PostConstruct
  void initialize() {
    applicationContext.getBeansOfType(StageBuilder).values().each {
      stages[it.type] = it
    }
    applicationContext.getBeansOfType(StandaloneTask).values().each {
      def stage = new SimpleStage(it.type, it)
      applicationContext.autowireCapableBeanFactory.autowireBean(stage)
      // TODO: this should be a prototype scoped bean or use a factory I guess
      stages[it.type] = stage
    }
  }

  T start(String configJson) {
    Map<String, Object> config = mapper.readValue(configJson, Map)
    def subject = createSubject(config)
    def job = build(config, subject)
    launcher.run job, createJobParameters(subject, config)
    subject
  }

  protected abstract T createSubject(Map<String, Object> config)

  protected abstract Job build(Map<String, Object> config, T subject)

  protected JobFlowBuilder createStage(JobFlowBuilder jobBuilder, Stage stage) {
    builderFor(stage).build(jobBuilder, stage)
  }

  protected StageBuilder builderFor(Stage stage) {
    if (stages.containsKey(stage.type)) {
      stages.get(stage.type)
    } else {
      throw new NoSuchStageException(stage.type)
    }
  }

  protected JobParameters createJobParameters(T subject, Map<String, Object> config) {
    def params = new JobParametersBuilder()
    if (config.containsKey("application")) {
      params.addString("application", config.application as String)
    }
    if (config.containsKey("name")) {
      params.addString("name", config.name as String)
    }
    if (config.containsKey("description")) {
      params.addString("description", config.description as String, false)
    }
    params.toJobParameters()
  }
}
