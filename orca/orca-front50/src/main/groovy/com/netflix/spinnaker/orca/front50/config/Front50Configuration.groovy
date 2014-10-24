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





package com.netflix.spinnaker.orca.front50.config

import groovy.transform.CompileStatic
import com.google.gson.Gson
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.GsonConverter
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ComponentScan("com.netflix.spinnaker.orca.front50.pipeline")
@CompileStatic
class Front50Configuration {

  @Autowired Client retrofitClient
  @Autowired RestAdapter.LogLevel retrofitLogLevel

  @Bean Endpoint front50Endpoint(
    @Value('${front50.baseUrl:http://front50.prod.netflix.net}') String front50BaseUrl) {
    newFixedEndpoint(front50BaseUrl)
  }

  @Bean Front50Service front50Service(Endpoint front50Endpoint, Gson gson) {
    new RestAdapter.Builder()
      .setEndpoint(front50Endpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setConverter(new GsonConverter(gson))
      .build()
      .create(Front50Service)
  }
}
