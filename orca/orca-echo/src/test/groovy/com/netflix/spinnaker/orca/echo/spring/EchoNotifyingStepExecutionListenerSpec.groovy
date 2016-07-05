/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.item.ExecutionContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class EchoNotifyingStepExecutionListenerSpec extends Specification {

  def echoService = Mock(EchoService)
  def executionRepository = Stub(ExecutionRepository)

  @Subject
  def echoListener = new EchoNotifyingStageExecutionListener(executionRepository, echoService)

  @Shared
  def pipeline = new Pipeline(application: "foo")
  @Shared
  def stage = new PipelineStage(pipeline, "test")
  @Shared
  def orchestrationStage = new OrchestrationStage(new Orchestration(), 'test')

  def "triggers an event when a task step starts"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> BatchStatus.STARTED
    }

    when:
    echoListener.beforeTask(stage, stepExecution)

    then:
    1 * echoService.recordEvent(_)
  }

  @Unroll
  def "triggers an event when a task step exits with #batchStatus / #exitStatus.exitCode"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> batchStatus
      getExitStatus() >> exitStatus
    }

    when:
    echoListener.afterTask(stage, stepExecution)

    then:
    invocations * echoService.recordEvent(_)

    where:
    invocations | batchStatus           | exitStatus
    1           | BatchStatus.COMPLETED | ExitStatus.COMPLETED
    2           | BatchStatus.COMPLETED | ExitStatus.FAILED // this happens when you have a handled error in a step
    2           | BatchStatus.STOPPED   | ExitStatus.STOPPED
    2           | BatchStatus.FAILED    | ExitStatus.FAILED
  }

  @Unroll
  def "does not trigger an event when a task step exits with #batchStatus"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> batchStatus
    }

    when:
    echoListener.afterTask(stage, stepExecution)

    then:
    0 * echoService._

    where:
    batchStatus          | _
    BatchStatus.STARTED  | _
    BatchStatus.STARTING | _
  }

  @Unroll
  def "does #action an event for #executionStatus"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> BatchStatus.COMPLETED
      getExecutionContext() >> new ExecutionContext([orcaTaskStatus: executionStatus])
    }

    when:
    echoListener.afterTask(new OrchestrationStage(new Orchestration(), ""), stepExecution)

    then:
    invocationCount * echoService._

    where:
    executionStatus << ExecutionStatus.values()
    invocationCount = EchoNotifyingStageExecutionListener.SUPPRESSED_EXECUTION_STATUSES.contains(executionStatus) ? 0 : 1
    action = invocationCount ? "trigger" : "not trigger"
  }

  @Unroll
  def "sends the correct data to echo when the step completes with #batchStatus / #exitStatus.exitCode"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> batchStatus
      getExitStatus() >> exitStatus
    }
    and:
    def message
    echoService.recordEvent(_) >> {
      message = it[0]
      return null
    }

    when:
    echoListener.afterTask(stage, stepExecution)

    then:
    message.details.source == "orca"
    message.details.application == pipeline.application
    message.details.type == "orca:${type}:$echoMessage"
    message.details.type instanceof String

    where:
    batchStatus           | exitStatus           | echoMessage | type
    BatchStatus.COMPLETED | ExitStatus.COMPLETED | "complete"  | 'task'
    BatchStatus.COMPLETED | ExitStatus.FAILED    | "failed"    | 'stage'
    BatchStatus.STOPPED   | ExitStatus.STOPPED   | "failed"    | 'stage'
    BatchStatus.FAILED    | ExitStatus.FAILED    | "failed"    | 'stage'
  }

  @Unroll
  def "sends correct standalone flag #expectedStandaloneFlag to echo when dealing with #description"() {
    given:
    def stepExecution = Stub(StepExecution) {
      getStatus() >> BatchStatus.COMPLETED
      getExitStatus() >> ExitStatus.COMPLETED
    }
    and:
    def message
    echoService.recordEvent(_) >> {
      message = it[0]
      return null
    }

    when:
    echoListener.afterTask(type, stepExecution)

    then:
    message.content.standalone == expectedStandaloneFlag

    where:
    type               | description     || expectedStandaloneFlag
    orchestrationStage | 'orchestration' || true
    stage              | 'pipeline'      || false
  }

  def "should record the step execution name"() {
    given:

    def stepExecution = Stub(StepExecution) {
      getStatus() >> BatchStatus.COMPLETED
      getExitStatus() >> ExitStatus.COMPLETED
      getStepName() >> 'test123.createDeploy'
    }

    and:
    def message
    echoService.recordEvent(_) >> {
      message = it[0]
      return null
    }

    when:
    echoListener.afterTask(stage, stepExecution)

    then:
    message.content.taskName == 'test123.createDeploy'
  }

}
