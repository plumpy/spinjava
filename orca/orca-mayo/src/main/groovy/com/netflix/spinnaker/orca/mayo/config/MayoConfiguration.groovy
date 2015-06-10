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

package com.netflix.spinnaker.orca.mayo.config

import static retrofit.Endpoints.newFixedEndpoint

import com.google.gson.Gson
import com.netflix.spinnaker.orca.echo.spring.DependentPipelineExecutionListener
import com.netflix.spinnaker.orca.mayo.DependentPipelineStarter
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.GsonConverter

@Configuration
@Import(RetrofitConfiguration)
@ConditionalOnProperty(value = 'mayo.baseUrl')
@CompileStatic
@ComponentScan("com.netflix.spinnaker.orca.mayo")
class MayoConfiguration {

  @Autowired
  Client retrofitClient

  @Autowired
  RestAdapter.LogLevel retrofitLogLevel

  @Bean
  Endpoint mayoEndpoint(
    @Value('${mayo.baseUrl}') String mayoBaseUrl) {
    newFixedEndpoint(mayoBaseUrl)
  }

  @Bean
  MayoService mayoService(Endpoint mayoEndpoint, Gson gson) {
    new RestAdapter.Builder()
      .setEndpoint(mayoEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(MayoService))
      .setConverter(new GsonConverter(gson))
      .build()
      .create(MayoService)
  }

  @Bean
  DependentPipelineExecutionListener dependentPipelineExecutionListener(
    ExecutionRepository executionRepository,
    MayoService mayoService,
    DependentPipelineStarter dependentPipelineStarter
    ) {
    new DependentPipelineExecutionListener(executionRepository, mayoService, dependentPipelineStarter)
  }

}
