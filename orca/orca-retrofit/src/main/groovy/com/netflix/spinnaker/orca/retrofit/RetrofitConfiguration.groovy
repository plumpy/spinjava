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


package com.netflix.spinnaker.orca.retrofit

import com.jakewharton.retrofit.Ok3Client
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import groovy.transform.CompileStatic
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import retrofit.RestAdapter.LogLevel
import retrofit.client.OkClient

@Configuration
@CompileStatic
@Import(OkHttp3ClientConfiguration)
@EnableConfigurationProperties
class RetrofitConfiguration {
  @Bean(name = ["retrofitClient"])
  @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
  Ok3Client ok3Client(Registry registry,
                      OkHttp3ClientConfiguration okHttpClientConfig,
                      Optional<List<RetrofitInterceptorProvider>> retrofitInterceptorProviders) {
    final String userAgent = "Spinnaker-${System.getProperty('spring.application.name', 'unknown')}/${getClass().getPackage().implementationVersion ?: '1.0'}"
    OkHttpClient.Builder builder = okHttpClientConfig.create()
    builder.addNetworkInterceptor(
      new Interceptor() {
        @Override
        Response intercept(Interceptor.Chain chain) throws IOException {
          def req = chain.request().newBuilder().header('User-Agent', userAgent).build()
          chain.proceed(req)
        }
      })

    (retrofitInterceptorProviders.orElse([])).each { provider ->
      provider.interceptors.each { interceptor ->
        builder.addInterceptor(interceptor);
      }
    }

    new Ok3Client(builder.build())
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  OkClient okClient(Registry registry,
                    @Qualifier("okHttpClientConfiguration") OkHttpClientConfiguration okHttpClientConfig,
                    Optional<List<RetrofitInterceptorProvider>> retrofitInterceptorProviders) {
    final String userAgent = "Spinnaker-${System.getProperty('spring.application.name', 'unknown')}/${getClass().getPackage().implementationVersion ?: '1.0'}"
    def cfg = okHttpClientConfig.create()
    cfg.networkInterceptors().add(new com.squareup.okhttp.Interceptor() {
      @Override
      com.squareup.okhttp.Response intercept(com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
        def req = chain.request().newBuilder().header('User-Agent', userAgent).build()
        chain.proceed(req)
      }
    })

    (retrofitInterceptorProviders.orElse([])).each { provider ->
      provider.legacyInterceptors.each { interceptor ->
        cfg.interceptors().add(interceptor)
      }
    }

    new OkClient(cfg)
  }

  @Bean
  LogLevel retrofitLogLevel(@Value('${retrofit.logLevel:BASIC}') String retrofitLogLevel) {
    return LogLevel.valueOf(retrofitLogLevel)
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  RetrofitExceptionHandler retrofitExceptionHandler() {
    new RetrofitExceptionHandler()
  }
}
