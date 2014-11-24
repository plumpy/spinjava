/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.persistence.jedis

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionStore
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.JedisCommands

@CompileStatic
class JedisPipelineStore extends AbstractJedisBackedExecutionStore<Pipeline> {
  @Autowired
  JedisPipelineStore(JedisCommands jedis, ObjectMapper mapper) {
    super(ExecutionStore.PIPELINE, Pipeline, jedis, mapper)
  }

  @Override
  List<Pipeline> all() {
    []
  }
}
