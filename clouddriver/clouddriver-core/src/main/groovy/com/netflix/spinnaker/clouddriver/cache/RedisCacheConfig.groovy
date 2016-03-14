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

package com.netflix.spinnaker.clouddriver.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.redis.JedisPoolSource
import com.netflix.spinnaker.cats.redis.JedisSource
import com.netflix.spinnaker.cats.redis.cache.RedisNamedCacheFactory
import com.netflix.spinnaker.cats.redis.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPool

import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnProperty('redis.connection')
class RedisCacheConfig {

  @Bean
  JedisSource jedisSource(JedisPool jedisPool) {
    new JedisPoolSource(jedisPool)
  }

  @Bean
  NamedCacheFactory cacheFactory(
    JedisSource jedisSource,
    ObjectMapper objectMapper,
    @Value('${redis.maxMsetSize:250000}') int maxMsetSize,
    @Value('${caching.maxMergeCount:2500}') int maxMergeCount,
    @Value('${caching.hashing.enabled:true}') boolean enableHashing) {
    new RedisNamedCacheFactory(jedisSource, objectMapper, maxMsetSize, maxMergeCount, enableHashing, null)
  }

  @Bean
  AgentIntervalProvider agentIntervalProvider(@Value('${redis.poll.intervalSeconds:30}') int pollIntervalSeconds, @Value('${redis.poll.timeoutSeconds:300}') int pollTimeoutSeconds) {
    new CustomSchedulableAgentIntervalProvider(TimeUnit.SECONDS.toMillis(pollIntervalSeconds), TimeUnit.SECONDS.toMillis(pollTimeoutSeconds))
  }

  @Bean
  @ConditionalOnProperty(value = 'caching.writeEnabled', matchIfMissing = true)
  AgentScheduler agentScheduler(JedisSource jedisSource, @Value('${redis.connection:redis://localhost:6379}') String redisConnection, AgentIntervalProvider agentIntervalProvider) {
    URI redisUri = URI.create(redisConnection)
    String redisHost = redisUri.getHost()
    int redisPort = redisUri.getPort()
    if (redisPort == -1) {
      redisPort = 6379
    }
    new ClusteredAgentScheduler(jedisSource, new DefaultNodeIdentity(redisHost, redisPort), agentIntervalProvider)
  }
}
