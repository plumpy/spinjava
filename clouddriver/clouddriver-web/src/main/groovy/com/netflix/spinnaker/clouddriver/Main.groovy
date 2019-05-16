/*
 * Copyright 2014-2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig
import org.springframework.boot.actuate.autoconfigure.elasticsearch.ElasticSearchJestHealthIndicatorAutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchAutoConfiguration
import org.springframework.boot.autoconfigure.elasticsearch.jest.JestAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import sun.net.InetAddressCachePolicy

import java.security.Security

@Configuration
@Import([
  WebConfig,
  SecurityConfig,
])
@ComponentScan([
  'com.netflix.spinnaker.config',
  'com.netflix.spinnaker.clouddriver.config'
])
@EnableAutoConfiguration(exclude = [
  BatchAutoConfiguration,
  GroovyTemplateAutoConfiguration,
  GsonAutoConfiguration,
  DataSourceAutoConfiguration,
  ElasticsearchAutoConfiguration,
  ElasticSearchJestHealthIndicatorAutoConfiguration,
  JestAutoConfiguration
])
@EnableScheduling
class Main extends SpringBootServletInitializer {

  private static final Map<String, String> DEFAULT_PROPS = [
    'netflix.environment'     : 'test',
    'netflix.account'         : '${netflix.environment}',
    'netflix.stack'           : 'test',
    'spring.config.additional-location' : '${user.home}/.spinnaker/',
    'spring.profiles.active'  : '${netflix.environment},local',
    // add the Spring Cloud Config "composite" profile to default to a configuration
    // source that won't prevent app startup if custom configuration is not provided
    'spring.profiles.include' : 'composite',
    'spring.config.name'      : 'spinnaker,${spring.application.name}'
  ]

  private static final Map<String, String> BOOTSTRAP_SYSTEM_PROPS = [
    'spring.application.name'               : 'clouddriver',
    // default locations must be included pending the resolution of https://github.com/spring-cloud/spring-cloud-commons/issues/466
    'spring.cloud.bootstrap.location'       : 'classpath:/,classpath:/config/,file:./,file:./config/,${user.home}/.spinnaker/',
    'spring.cloud.bootstrap.name'           : 'spinnakerconfig,${spring.application.name}config',
    'spring.cloud.config.server.bootstrap'  : 'true'
  ]

  static {
    /**
     * We often operate in an environment where we expect resolution of DNS names for remote dependencies to change
     * frequently, so it's best to tell the JVM to avoid caching DNS results internally.
     */
    InetAddressCachePolicy.cachePolicy = InetAddressCachePolicy.NEVER
    Security.setProperty('networkaddress.cache.ttl', '0')
    System.setProperty("spring.main.allow-bean-definition-overriding", "true")
  }

  static void main(String... args) {
    BOOTSTRAP_SYSTEM_PROPS.findAll { key, value -> !System.getProperty(key)}
      .each { key, value -> System.setProperty(key, value)}
    new SpringApplicationBuilder()
      .properties(DEFAULT_PROPS)
      .sources(Main)
      .run(args)
  }

  @Bean
  @Primary
  ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder.createXmlMapper(false).build()
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application
      .properties(DEFAULT_PROPS)
      .sources(Main)
  }
}

