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

package com.netflix.spinnaker.orca.config

import com.fasterxml.jackson.databind.module.SimpleModule
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.jackson.StageDeserializer
import com.netflix.spinnaker.orca.jackson.StageSerializer
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.batch.StageStatusPropagationListener
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.notifications.NoopNotificationHandler
import com.netflix.spinnaker.orca.pipeline.OrchestrationStarter
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan("com.netflix.spinnaker.orca.pipeline")
@CompileStatic
class OrcaConfiguration {

  @Bean ObjectMapper mapper() {
    new OrcaObjectMapper()
  }

  @Bean ExecutionStore<Orchestration> orchestrationStore(ObjectMapper mapper) {
    new InMemoryOrchestrationStore(mapper)
  }

  @Bean ExecutionStore<Pipeline> pipelineStore(ObjectMapper mapper) {
    new InMemoryPipelineStore(mapper)
  }

  @Bean ExecutionRepository executionRepository(ExecutionStore<Pipeline> pipelineStore,
                                                ExecutionStore<Orchestration> orchestrationStore) {
    new DefaultExecutionRepository(orchestrationStore, pipelineStore)
  }

  @Bean PipelineStarter jobStarter() {
    new PipelineStarter()
  }

  @Bean OrchestrationStarter orchestrationStarter() {
    new OrchestrationStarter()
  }

  @Bean NoopNotificationHandler noopNotificationHandler() {
    new NoopNotificationHandler()
  }

  @Bean TaskTaskletAdapter taskTaskletAdapter(ExecutionRepository executionRepository) {
    new TaskTaskletAdapter(executionRepository)
  }

  @Bean StageStatusPropagationListener stageStatusPropagationListener(ExecutionRepository executionRepository) {
    new StageStatusPropagationListener(executionRepository)
  }
}
