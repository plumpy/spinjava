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

package com.netflix.spinnaker.orca.test.redis

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import net.greghaines.jesque.Config
import net.greghaines.jesque.ConfigBuilder
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool

/**
 *
 * @author sthadeshwar
 */
@Configuration
class EmbeddedRedisConfiguration {

  @Bean
  EmbeddedRedis redisServer() {
    def redis = EmbeddedRedis.embed()
    def jedis = redis.jedis
    jedis.flushAll()
    jedis.close()
    return redis
  }

  @Bean
  Config jesqueConfig() {
    new ConfigBuilder().withHost("localhost")
                       .withPort(redisServer().redisServer.port)
                       .build()
  }

  @Bean
  Pool<Jedis> jedisPool() {
    new JedisPool("localhost", redisServer().redisServer.port)
  }

  @Bean
  @Scope(BeanDefinition.SCOPE_PROTOTYPE)
  JedisCommands jedisCommands(Pool<Jedis> pool) {
    pool.resource
  }

}
