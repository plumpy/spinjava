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

package com.netflix.spinnaker.kato.orchestration

import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class DefaultOrchestrationProcessorSpec extends Specification {

  @Shared
  DefaultOrchestrationProcessor processor

  @Shared
  ApplicationContext applicationContext

  @Shared
  TaskRepository taskRepository

  def setup() {
    processor = new DefaultOrchestrationProcessor()
    applicationContext = Mock(ApplicationContext)
    applicationContext.getAutowireCapableBeanFactory() >> Mock(AutowireCapableBeanFactory)
    taskRepository = Mock(TaskRepository)
    processor.applicationContext = applicationContext
    processor.taskRepository = taskRepository
  }

  void "complete the task when everything goes as planned"() {
    setup:
    def task = Mock(Task)
    def atomicOperation = Mock(AtomicOperation)

    when:
    submitAndWait atomicOperation

    then:
    1 * taskRepository.create(_, _) >> task
    1 * task.complete()
  }

  void "fail the task when exception is thrown"() {
    setup:
    def task = Mock(Task)
    def atomicOperation = Mock(AtomicOperation)

    when:
    submitAndWait atomicOperation

    then:
    1 * taskRepository.create(_, _) >> task
    1 * atomicOperation.operate(_) >> { throw new RuntimeException() }
    1 * task.fail()
  }

  private void submitAndWait(AtomicOperation atomicOp) {
    processor.process([atomicOp])
    processor.executorService.shutdown()
    processor.executorService.awaitTermination(5, TimeUnit.SECONDS)
  }
}
