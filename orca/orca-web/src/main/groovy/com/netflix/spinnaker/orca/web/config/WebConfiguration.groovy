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

package com.netflix.spinnaker.orca.web.config

import javax.servlet.*
import javax.servlet.http.HttpServletResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.data.jackson.StageMixins
import org.springframework.batch.core.configuration.ListableJobLocator
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.SimpleJobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan(basePackages = 'com.netflix.spinnaker.orca.controllers')
class WebConfiguration {
  @Bean
  BatchConfigurer batchConfigurer() {
    new MultiThreadedBatchConfigurer()
  }

  @Bean
  JobOperator jobOperator(JobLauncher jobLauncher, JobRepository jobRepository, JobExplorer jobExplorer, ListableJobLocator jobRegistry) {
    def jobOperator = new SimpleJobOperator()
    jobOperator.jobLauncher = jobLauncher
    jobOperator.jobRepository = jobRepository
    jobOperator.jobExplorer = jobExplorer
    jobOperator.jobRegistry = jobRegistry
    return jobOperator
  }

  @Bean
  ObjectMapper objectMapper() {
    def mapper = new ObjectMapper()
    mapper.addMixInAnnotations(PipelineStage, StageMixins)
    mapper
  }

  @Bean
  Filter simpleCORSFilter() {
    new Filter() {
      public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type");
        chain.doFilter(req, res);
      }

      public void init(FilterConfig filterConfig) {}

      public void destroy() {}
    }
  }
}
