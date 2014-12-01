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

import groovy.transform.CompileStatic
import javax.annotation.PostConstruct
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

@CompileStatic
abstract class AbstractOrchestrationInitiator<T extends Execution> {

  private final String type

  AbstractOrchestrationInitiator(String type) {
    this.type = type
  }

  @Autowired protected ApplicationContext applicationContext
  @Autowired protected JobLauncher launcher
  @Autowired protected JobBuilderFactory jobs
  @Autowired protected StepBuilderFactory steps
  @Autowired protected ObjectMapper mapper

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
    def subject = create(config)
    persistExecution(subject)
    def job = build(config, subject)
    persistExecution(subject)
    JobExecution execution = launcher.run job, createJobParameters(subject, config)
    if (subject instanceof Orchestration) {
      subject.id = execution.jobId.toString()
    }
    subject
  }

  protected abstract void persistExecution(T subject)

  protected abstract T create(Map<String, Object> config)

  protected abstract Job build(Map<String, Object> config, T subject)

  protected JobParameters createJobParameters(T subject, Map<String, Object> config) {
    def params = new JobParametersBuilder()
    params.addString(type, subject.id)
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
