/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kato.config

import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.data.task.jedis.JedisTaskRepository
import groovy.transform.CompileStatic
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for embedded redis instance and associated jedis
 **/
@Configuration
@CompileStatic
@ConditionalOnProperty('redis.connection')
class JedisRepositoryConfig {

  @Bean
  TaskRepository taskRepository() {
    new JedisTaskRepository()
  }
}
