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

package com.netflix.spinnaker.kork.jedis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisDataException;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Configuration for embedded redis instance and associated jedis
 */
@ConditionalOnMissingClass(name = "com.netflix.dyno.jedis.DynoJedisClient")
public class JedisConfig {

  @Autowired
  Environment environment;

  private static final Logger log = LoggerFactory.getLogger(JedisConfig.class);

  @Bean
  public JedisCommands jedis(@Value("${redis.port:0}") int port,
                             @Value("${redis.host:127.0.0.1}") String host,
                             @Value("${redis.connection:none}") String connection
  ) {
    if (connection != null) {
      try {
        URI jedisConnection = new URI(connection);
      } catch (URISyntaxException e) {

      }
    }
    return new JedisPool()
  }

  private static class JedisDelegatingMethodInvocationHandler implements InvocationHandler {

    private final JedisPool delegate;

    JedisDelegatingMethodInvocationHandler(JedisPool delegate) {
      this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      Jedis jedis = delegate.getResource();
      try {
        Object result = method.invoke(jedis, args);
        delegate.returnResource(jedis);
        return result;
      } catch (InvocationTargetException ex) {
        delegate.returnBrokenResource(jedis);
        throw ex.getTargetException();
      }
    }
  }

}
