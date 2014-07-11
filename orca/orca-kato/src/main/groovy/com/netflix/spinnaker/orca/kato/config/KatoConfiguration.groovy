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





package com.netflix.spinnaker.orca.kato.config

import com.netflix.spinnaker.orca.kato.pipeline.CopyLastAsgStageBuilder
import groovy.transform.CompileStatic
import com.google.gson.Gson
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.pipeline.DeployStageBuilder
import com.netflix.spinnaker.orca.kato.pipeline.DestroyAsgStageBuilder
import com.netflix.spinnaker.orca.kato.pipeline.DisableAsgStageBuilder
import com.netflix.spinnaker.orca.kato.pipeline.EnableAsgStageBuilder
import com.netflix.spinnaker.orca.kato.pipeline.ResizeAsgStageBuilder
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.converter.GsonConverter
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@CompileStatic
class KatoConfiguration {

  @Autowired Client retrofitClient
  @Autowired LogLevel retrofitLogLevel

  @Bean Endpoint katoEndpoint() {
    newFixedEndpoint("http://kato.prod.netflix.net")
  }

  @Bean KatoService katoDeployService(Endpoint katoEndpoint, Gson gson) {
    new RestAdapter.Builder()
        .setEndpoint(katoEndpoint)
        .setClient(retrofitClient)
        .setLogLevel(retrofitLogLevel)
        .setConverter(new GsonConverter(gson))
        .build()
        .create(KatoService)
  }

  @Bean DeployStageBuilder deployStageBuilder() {
    new DeployStageBuilder()
  }

  @Bean CopyLastAsgStageBuilder copyLastAsgStageBuilder() {
    new CopyLastAsgStageBuilder()
  }

  @Bean DestroyAsgStageBuilder destroyAsgStageBuilder() {
    new DestroyAsgStageBuilder()
  }

  @Bean DisableAsgStageBuilder disableAsgStageBuilder() {
    new DisableAsgStageBuilder()
  }

  @Bean EnableAsgStageBuilder enableAsgStageBuilder() {
    new EnableAsgStageBuilder()
  }

  @Bean ResizeAsgStageBuilder resizeAsgStageBuilder() {
    new ResizeAsgStageBuilder()
  }
}
