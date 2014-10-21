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

package com.netflix.spinnaker.orca.batch.lifecycle

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.Pipeline
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobBuilder
import rx.subjects.ReplaySubject
import static com.netflix.spinnaker.orca.PipelineStatus.RUNNING
import static com.netflix.spinnaker.orca.PipelineStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep

class StartAndMonitorExecutionSpec extends BatchExecutionSpec {

  def startTask = Stub(Task)
  def monitorTask = Mock(Task)

  def "can start an external service and monitor until completed"() {
    given:
    startTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    when:
    def jobExecution = launchJob()

    then:
    3 * monitorTask.execute(_) >> new DefaultTaskResult(RUNNING) >> new DefaultTaskResult(RUNNING) >> new DefaultTaskResult(SUCCEEDED)

    and:
    jobExecution.exitStatus == ExitStatus.COMPLETED
  }

  def "abandons monitoring if the monitor task fails"() {
    given:
    startTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    when:
    def jobExecution = launchJob()

    then:
    2 * monitorTask.execute(_) >> new DefaultTaskResult(RUNNING) >> {
      throw new RuntimeException("something went wrong")
    }

    and:
    jobExecution.exitStatus == ExitStatus.FAILED
  }

  def "does not start monitoring if the start task fails"() {
    given:
    startTask.execute(_) >> { throw new RuntimeException("something went wrong") }

    when:
    def jobExecution = launchJob()

    then:
    0 * monitorTask._

    and:
    jobExecution.exitStatus == ExitStatus.FAILED
  }

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    def pipeline = Pipeline.builder().withStage("startAndMonitor").build()
    def subject = ReplaySubject.create(1)
    def builder = jobBuilder.flow(initializationStep(steps, pipeline))
    new StartAndMonitorStage(steps: steps, startTask: startTask, monitorTask: monitorTask)
      .build(builder, pipeline.namedStage("startAndMonitor"))
      .build()
      .build()
  }
}
