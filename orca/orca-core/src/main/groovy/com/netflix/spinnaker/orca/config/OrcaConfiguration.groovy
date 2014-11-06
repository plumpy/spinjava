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

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.notifications.NoopNotificationHandler
import com.netflix.spinnaker.orca.pipeline.OrchestrationStarter
import com.netflix.spinnaker.orca.pipeline.PipelineFactory
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.retry.backoff.ThreadWaitSleeper

@Configuration
@ComponentScan("com.netflix.spinnaker.orca.pipeline")
@CompileStatic
class OrcaConfiguration {

  @Bean ObjectMapper mapper() {
    def mapper = new ObjectMapper()
    mapper.registerModule(new GuavaModule())
    return mapper
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

  @Bean PipelineFactory pipelineFactory(JobExplorer jobExplorer) {
    new PipelineFactory(jobExplorer)
  }

  @Bean TaskTaskletAdapter taskTaskletAdapter() {
    new TaskTaskletAdapter(new ThreadWaitSleeper())
  }
}
