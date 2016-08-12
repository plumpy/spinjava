/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.front50.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

import javax.servlet.Filter

@Configuration
@ComponentScan
public class Front50WebConfig extends WebMvcConfigurerAdapter {
  @Autowired
  Registry registry

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new MetricsInterceptor(
            this.registry, "controller.invocations", ["account", "application"], ["BasicErrorController"]
        )
    )
  }

  @Bean
  Filter authenticatedRequestFilter() {
    new AuthenticatedRequestFilter(true)
  }

  @Bean
  ItemDAOHealthIndicator applicationDAOHealthIndicator(ApplicationDAO applicationDAO) {
    return new ItemDAOHealthIndicator(itemDAO: applicationDAO)
  }

  @Bean
  ItemDAOHealthIndicator projectDAOHealthIndicator(ProjectDAO projectDAO) {
    return new ItemDAOHealthIndicator(itemDAO: projectDAO)
  }

  @Bean
  ItemDAOHealthIndicator pipelineDAOHealthIndicator(PipelineDAO pipelineDAO) {
    return new ItemDAOHealthIndicator(itemDAO: pipelineDAO)
  }

  @Bean
  ItemDAOHealthIndicator pipelineStrategyDAOHealthIndicator(PipelineStrategyDAO pipelineStrategyDAO) {
    return new ItemDAOHealthIndicator(itemDAO: pipelineStrategyDAO)
  }
}
