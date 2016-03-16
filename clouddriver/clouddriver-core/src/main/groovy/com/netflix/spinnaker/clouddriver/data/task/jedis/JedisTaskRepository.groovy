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

package com.netflix.spinnaker.clouddriver.data.task.jedis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.Status
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskDisplayStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

import java.util.concurrent.TimeUnit

class JedisTaskRepository implements TaskRepository {
  private static final String RUNNING_TASK_KEY = "kato:tasks"
  private static final TypeReference<Map<String, String>> HISTORY_TYPE = new TypeReference<Map<String, String>>() {}

  private static final int TASK_TTL = (int) TimeUnit.HOURS.toSeconds(12);

  @Autowired
  JedisPool jedisPool

  ObjectMapper mapper = new ObjectMapper()

  @Override
  Task create(String phase, String status) {
    String taskId = jedis { it.incr('taskCounter') }
    def task = new JedisTask(taskId, System.currentTimeMillis(), this)
    addToHistory(new DefaultTaskStatus(phase, status, TaskState.STARTED), task)
    set(taskId, task)
    task
  }

  @Override
  Task get(String id) {
    Map<String, String> taskMap = jedis { it.hgetAll("task:${id}") }
    new JedisTask(
      taskMap.id,
      Long.parseLong(taskMap.startTimeMs),
      this)
  }

  @Override
  List<Task> list() {
    jedis { it.smembers(RUNNING_TASK_KEY) }.collect { key ->
      get(key)
    }
  }

  void set(String id, JedisTask task) {
    String taskId = "task:${task.id}"
    jedis {
      def pipe = it.pipelined()
      pipe.hset(taskId, 'id', task.id)
      pipe.hset(taskId, 'startTimeMs', task.startTimeMs as String)
      pipe.sadd(RUNNING_TASK_KEY, id)
      pipe.expire(taskId, TASK_TTL)
      pipe.sync()
    }
  }

  void addResultObjects(List<Object> objects, JedisTask task) {
    String resultId = "taskResult:${task.id}"
    String[] values = objects.collect { mapper.writeValueAsString(it) }
    jedis {
      def pipe = it.pipelined()
      pipe.rpush(resultId, values)
      pipe.expire(resultId, TASK_TTL)
      pipe.sync()
    }
  }

  List<Object> getResultObjects(JedisTask task) {
    String resultId = "taskResult:${task.id}"
    jedis { it.lrange(resultId, 0, -1) }.collect { mapper.readValue(it, Map) }
  }

  DefaultTaskStatus currentState(JedisTask task) {
    String historyId = "taskHistory:${task.id}"
    Map<String, String> history = mapper.readValue(jedis { it.lindex(historyId, -1) }, HISTORY_TYPE)
    new DefaultTaskStatus(history.phase, history.status, TaskState.valueOf(history.state))
  }

  void addToHistory(DefaultTaskStatus status, JedisTask task) {
    String historyId = "taskHistory:${task.id}"
    def hist = mapper.writeValueAsString([phase: status.phase, status: status.status, state: status.state.toString()])
    jedis {
      def pipe = it.pipelined()
      pipe.rpush(historyId, hist)
      if (status.isCompleted()) {
        pipe.srem(RUNNING_TASK_KEY, task.id)
      }
      pipe.expire(historyId, TASK_TTL)
      pipe.sync()
    }
  }

  List<Status> getHistory(JedisTask task) {
    String historyId = "taskHistory:${task.id}"
    jedis { it.lrange(historyId, 0, -1) }.collect {
      Map<String, String> history = mapper.readValue(it, HISTORY_TYPE)
      new TaskDisplayStatus(new DefaultTaskStatus(history.phase, history.status, TaskState.valueOf(history.state)))
    }
  }

  private <T> T jedis(@ClosureParams(value = SimpleType,
    options = ['redis.clients.jedis.Jedis']) Closure<T> withJedis) {
    jedisPool.getResource().withCloseable {
      Jedis jedis = (Jedis) it
      return withJedis.call(jedis)
    }
  }

}
