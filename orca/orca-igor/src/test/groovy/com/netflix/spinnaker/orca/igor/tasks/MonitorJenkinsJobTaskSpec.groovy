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

package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorJenkinsJobTaskSpec extends Specification {

  @Subject MonitorJenkinsJobTask task = new MonitorJenkinsJobTask()

  @Shared Pipeline pipeline = new Pipeline()

  @Unroll
  def "should return #taskStatus if job is #jobState"() {
    given:
    def stage = new PipelineStage(pipeline, "jenkins", [master: "builds", job: "orca", buildNumber: 4]).asImmutable()

    and:
    task.igorService = Stub(IgorService) {
      getBuild(stage.context.master, stage.context.job, stage.context.buildNumber) >> [ result : jobState ]
    }

    expect:
    task.execute(stage).status == taskStatus

    where:
    jobState   | taskStatus
    'ABORTED'  | ExecutionStatus.CANCELED
    'FAILURE'  | ExecutionStatus.FAILED
    'SUCCESS'  | ExecutionStatus.SUCCEEDED
    'UNSTABLE' | ExecutionStatus.SUCCEEDED
    null       | ExecutionStatus.RUNNING
    'UNKNOWN'  | ExecutionStatus.RUNNING
  }

  def "should return running status if igor call 404's"() {
    given:
    def stage = new PipelineStage(pipeline, "jenkins", [master: "builds", job: "orca", buildNumber: 4]).asImmutable()

    and:
    def exception = Stub(RetrofitError) {
      getResponse() >> new Response('', 404, '', [], null)
    }

    task.igorService = Stub(IgorService) {
      getBuild(stage.context.master, stage.context.job, stage.context.buildNumber) >> { throw exception }
    }

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING
  }
}
