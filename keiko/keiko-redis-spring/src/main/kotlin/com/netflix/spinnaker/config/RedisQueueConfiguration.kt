/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config

import com.netflix.spinnaker.q.redis.RedisDeadMessageHandler
import com.netflix.spinnaker.q.redis.RedisQueue
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.time.Clock
import java.time.Duration

@Configuration
@EnableConfigurationProperties(RedisQueueProperties::class)
open class RedisQueueConfiguration {

  @Bean() open fun redisDeadMessageHandler(
    redisPool: Pool<Jedis>,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock
  ) =
    RedisDeadMessageHandler(
      deadLetterQueueName = redisQueueProperties.deadLetterQueueName,
      pool = redisPool,
      clock = clock
    )

  @Bean open fun queue(
    redisPool: Pool<Jedis>,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock,
    deadMessageHandler: RedisDeadMessageHandler,
    publisher: ApplicationEventPublisher
  ) =
    RedisQueue(
      queueName = redisQueueProperties.queueName,
      pool = redisPool,
      clock = clock,
      deadMessageHandler = deadMessageHandler::handle,
      publisher = publisher,
      ackTimeout = Duration.ofSeconds(redisQueueProperties.ackTimeoutSeconds.toLong())
    )

}
