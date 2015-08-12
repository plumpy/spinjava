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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

@Unroll
class PipelineSpec extends Specification {

  @Subject pipeline = Pipeline.builder()
                              .withTrigger(name: "SPINNAKER-build-job", lastBuildLabel: 1)
                              .withStage("stage1")
                              .withStage("stage2")
                              .withStage("stage3")
                              .build()

  void setup() {
    pipeline.stages.findAll { it.tasks.isEmpty() }.each {
      // ensure each stage has at least one task (otherwise it will get skipped when calculating pipeline status)
      it.tasks << new DefaultTask()
    }
  }

  def "a pipeline's status is #expectedStatus if one of its stages is #expectedStatus"() {
    given:
    pipeline.stages[0].status = stage1Status
    pipeline.stages[1].status = stage2Status
    pipeline.stages[2].status = stage3Status

    // add an empty stage (no tasks), should have no affect on pipeline status
    pipeline.stages.add(new PipelineStage(pipeline, "stage4"))

    expect:
    pipeline.status == expectedStatus

    where:
    stage1Status | stage2Status | stage3Status | expectedStatus
    NOT_STARTED  | NOT_STARTED  | NOT_STARTED  | NOT_STARTED
    SUCCEEDED    | RUNNING      | NOT_STARTED  | RUNNING
    SUCCEEDED    | SUCCEEDED    | NOT_STARTED  | RUNNING
    SUCCEEDED    | SUCCEEDED    | RUNNING      | RUNNING
    RUNNING      | SUCCEEDED    | SUCCEEDED    | RUNNING
    SUCCEEDED    | RUNNING      | SUCCEEDED    | RUNNING
    SUSPENDED    | RUNNING      | NOT_STARTED  | RUNNING
    RUNNING      | SUSPENDED    | NOT_STARTED  | RUNNING
    SUSPENDED    | SUCCEEDED    | NOT_STARTED  | SUSPENDED
    SUCCEEDED    | SUCCEEDED    | SUCCEEDED    | SUCCEEDED
    SUCCEEDED    | FAILED       | NOT_STARTED  | FAILED
    SUCCEEDED    | SUCCEEDED    | FAILED       | FAILED
    FAILED       | SUCCEEDED    | RUNNING      | FAILED
    SUCCEEDED    | SUSPENDED    | NOT_STARTED  | SUSPENDED
    SUCCEEDED    | SUCCEEDED    | SUSPENDED    | SUSPENDED
    TERMINAL     | SUCCEEDED    | NOT_STARTED  | TERMINAL
    SUCCEEDED    | TERMINAL     | NOT_STARTED  | TERMINAL
    SUCCEEDED    | SUCCEEDED    | TERMINAL     | TERMINAL
  }

  def "a pipeline containing no stages >= 1 tasks should be NOT_STARTED"() {
    when:
    pipeline.stages.each {
      it.status = RUNNING
      it.tasks.clear()
    }

    then:
    pipeline.status == NOT_STARTED

    when:
    pipeline.stages[0].tasks << new DefaultTask()

    then:
    pipeline.status == RUNNING
  }

  def "can get a previous stage from a stage by type"() {
    expect:
    pipeline.namedStage("stage2").preceding("stage1") is pipeline.stages[0]
  }

  def "trigger is properly build into the pipeline"() {
    expect:
    pipeline.trigger.name == "SPINNAKER-build-job" && pipeline.trigger.lastBuildLabel == 1
  }

  @Unroll
  def "should resolve start and end time properly"() {
    when:
    pipeline.stages[0].startTime = stage1StartTime
    pipeline.stages[0].endTime = stage1EndTime
    pipeline.stages[0].status = stage1Status
    pipeline.stages[1].startTime = stage2StartTime
    pipeline.stages[1].endTime = stage2EndTime
    pipeline.stages[1].status = stage2Status
    pipeline.stages[2].startTime = stage2StartTime
    pipeline.stages[2].endTime = stage2EndTime
    pipeline.stages[2].status = stage2Status

    then:
    pipeline.startTime == pipelineStart

    and:
    pipeline.endTime == pipelineEnd

    where:
    stage1StartTime | stage1EndTime | stage1Status | stage2StartTime | stage2EndTime | stage2Status | pipelineStart | pipelineEnd
    1L              | 2L            | SUCCEEDED    | 2L              | 3L            | SUCCEEDED    | 1L            | 3L
    1L              | 2L            | SUCCEEDED    | 2L              | null          | RUNNING      | 1L            | null
    1L              | null          | RUNNING      | 1L              | 2L            | SUCCEEDED    | 1L            | null
    null            | null          | NOT_STARTED  | null            | 2L            | NOT_STARTED  | null          | null
    1L              | 1L            | FAILED       | null            | null          | NOT_STARTED  | 1L            | 1L
  }

}
