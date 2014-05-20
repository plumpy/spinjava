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

package com.netflix.bluespar.oort

import com.netflix.appinfo.InstanceInfo
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springsource.loaded.agent.SpringLoadedPreProcessor
import org.springsource.loaded.agent.SpringPlugin

@EnableAutoConfiguration
@Configuration
@ComponentScan("com.netflix.bluespar")
@EnableScheduling
class Main extends SpringBootServletInitializer {

  static void main(_) {
    def springPlugin = SpringLoadedPreProcessor.globalPlugins.find { it instanceof SpringPlugin }
    if (springPlugin) {
      SpringLoadedPreProcessor.unregisterGlobalPlugin springPlugin
    }
    SpringApplication.run this, [] as String[]
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application.sources Main
  }

  @Bean
  InstanceInfo.InstanceStatus instanceStatus() {
    InstanceInfo.InstanceStatus.UNKNOWN
  }
}
