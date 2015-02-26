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

package com.netflix.spinnaker.gate

import com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.embedded.ServletRegistrationBean
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@Configuration
@ComponentScan(["com.netflix.spinnaker.gate", "com.netflix.spinnaker.config"])
@EnableAutoConfiguration(exclude = [SecurityAutoConfiguration, GroovyTemplateAutoConfiguration])
class Main extends SpringBootServletInitializer {
  static final Map<String, String> DEFAULT_PROPS = [
          'netflix.environment': System.getProperty('netflix.environment', 'test'),
          'netflix.account': System.getProperty('netflix.environment', 'test'),
          'netflix.stack': System.getProperty('netflix.stack', 'test'),
          'spring.config.location': "${System.properties['user.home']}/.spinnaker/",
          'spring.config.name': 'gate',
          'spring.profiles.active': "${System.getProperty('netflix.environment', 'test')},local"
  ]

  static {
    applyDefaults()
  }

  static void applyDefaults() {
    DEFAULT_PROPS.each { k, v ->
      System.setProperty(k, System.getProperty(k, v))
    }
  }

  static void main(String... args) {
    SpringApplication.run this, args
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
    builder.sources(Main)
  }

  @Bean
  ServletRegistrationBean hystrixEventStream() {
    new ServletRegistrationBean(new HystrixMetricsStreamServlet(), '/hystrix.stream')
  }
}
