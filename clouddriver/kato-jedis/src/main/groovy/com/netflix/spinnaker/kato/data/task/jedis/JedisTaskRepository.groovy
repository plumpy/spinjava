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

package com.netflix.spinnaker.kato.data.task.jedis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kato.data.task.DefaultTaskStatus
import com.netflix.spinnaker.kato.data.task.Status
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskDisplayStatus
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.data.task.TaskState
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.JedisCommands

class JedisTaskRepository implements TaskRepository {

  private static final TypeReference<Map<String, String>> HISTORY_TYPE = new TypeReference<Map<String, String>>() {}

  @Autowired
  JedisCommands jedis

  ObjectMapper mapper = new ObjectMapper()

  @Override
  Task create(String phase, String status) {
    String taskId = jedis.incr('taskCounter')
    def task = new JedisTask(taskId, System.currentTimeMillis(), this)
    addToHistory(new DefaultTaskStatus(phase, status, TaskState.STARTED), task)
    set(taskId, task)
    task
  }

  @Override
  Task get(String id) {
    Map<String, String> taskMap = jedis.hgetAll("task:${id}")
    new JedisTask(
      taskMap.id,
      Long.parseLong(taskMap.startTimeMs),
      this)
  }

  @Override
  List<Task> list() {
    jedis.keys('task:*').collect { key ->
      get(key - 'task:')
    }
  }

  void set(String id, JedisTask task) {
    String taskId = "task:${task.id}"
    jedis.hset(taskId, 'id', task.id)
    jedis.hset(taskId, 'startTimeMs', task.startTimeMs as String)
  }

  void addResultObjects(List<Object> objects, JedisTask task) {
    String resultId = "taskResult:${task.id}"
    String[] values = objects.collect { mapper.writeValueAsString(it) }
    jedis.rpush(resultId, values)
  }

  List<Object> getResultObjects(JedisTask task) {
    String resultId = "taskResult:${task.id}"
    jedis.lrange(resultId, 0, -1).collect { mapper.readValue(it, Map) }
  }

  DefaultTaskStatus currentState(JedisTask task) {
    String historyId = "taskHistory:${task.id}"
    Map<String, String> history = mapper.readValue(jedis.lindex(historyId, -1), HISTORY_TYPE)
    new DefaultTaskStatus(history.phase, history.status, TaskState.valueOf(history.state))
  }

  void addToHistory(DefaultTaskStatus status, JedisTask task) {
    String historyId = "taskHistory:${task.id}"
    jedis.rpush(historyId, mapper.writeValueAsString([phase: status.phase, status: status.status, state:status.state.toString()]))
  }

  List<Status> getHistory(JedisTask task) {
    String historyId = "taskHistory:${task.id}"
    jedis.lrange(historyId, 0, -1).collect {
      Map<String, String> history = mapper.readValue(it, HISTORY_TYPE)
      new TaskDisplayStatus(new DefaultTaskStatus(history.phase, history.status, TaskState.valueOf(history.state)))
    }
  }

}
