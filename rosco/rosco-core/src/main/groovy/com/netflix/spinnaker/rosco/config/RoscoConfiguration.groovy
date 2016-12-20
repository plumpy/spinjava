/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.config

import com.netflix.spinnaker.rosco.executor.BakePoller
import com.netflix.spinnaker.rosco.persistence.BakeStore
import com.netflix.spinnaker.rosco.persistence.RedisBackedBakeStore
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.registry.DefaultCloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.util.LocalJobFriendlyPackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import groovy.transform.CompileStatic
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.DefaultConversionService
import redis.clients.jedis.JedisPool

@Configuration
@CompileStatic
class RoscoConfiguration {

  @Bean
  String roscoInstanceId() {
    UUID.randomUUID().toString()
  }

  @Bean
  @ConditionalOnMissingBean(BakePoller)
  BakePoller bakePoller() {
    new BakePoller()
  }

  @Bean
  BakeStore bakeStore(JedisPool jedisPool) {
    new RedisBackedBakeStore(jedisPool)
  }

  @Bean
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry() {
    return new DefaultCloudProviderBakeHandlerRegistry()
  }

  // Allows @Value annotation to tokenize a list of strings.
  @Bean
  ConversionService conversionService() {
    return new DefaultConversionService()
  }

  @Bean
  @ConditionalOnMissingBean(PackerCommandFactory)
  PackerCommandFactory localJobFriendlyPackerCommandFactory() {
    return new LocalJobFriendlyPackerCommandFactory()
  }

}
