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

package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedInput
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UpsertSecurityGroupTaskSpec extends Specification {

  @Subject task = new UpsertSecurityGroupTask()

  @Unroll
  void "should #includeLabel current value ('#current') when Mort returns it"() {
    given:
    def pipeline = new Pipeline()
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        rx.Observable.from(new TaskId(UUID.randomUUID().toString()))
      }
    }
    task.mapper = new OrcaObjectMapper()
    def groupName = 'group'
    def account = 'account'
    def region = 'region'

    def response = GroovyMock(Response)
    response.getStatus() >> 200
    response.getBody() >> {
      def input = Mock(TypedInput)
      input.in() >> new ByteArrayInputStream(current.bytes)
      input
    }

    task.mortService = Stub(MortService) {
      getSecurityGroup(account, 'aws', groupName, region) >> response
    }

    and:
    def stage = new PipelineStage(pipeline, "whatever", [
      credentials: account,
      region     : region,
      name       : groupName
    ]).asImmutable()

    when:
    def executionContext = task.execute(stage)

    then:
    executionContext.outputs.containsKey("upsert.pre.response") == expected
    if (expected) {
      executionContext.outputs."upsert.pre.response" == current
    }

    where:
    current  || expected
    ''       || false
    'exists' || true

    includeLabel = expected ? 'include' : 'exclude'
  }
}
