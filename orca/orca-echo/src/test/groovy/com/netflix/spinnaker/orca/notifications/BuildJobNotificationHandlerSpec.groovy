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


package com.netflix.spinnaker.orca.notifications

import groovy.json.JsonSlurper
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import retrofit.client.Response
import retrofit.mime.TypedInput
import spock.lang.Specification

class BuildJobNotificationHandlerSpec extends Specification {

  def pipeline1 = [
    name    : "pipeline1",
    triggers: [[type  : "jenkins",
                job   : "SPINNAKER-package-pond",
                master: "master1"]],
    stages  : [[type: "bake"],
               [type: "deploy", cluster: [name: "bar"]]]
  ]

  def pipeline2 = [
    name    : "pipeline2",
    triggers: [[type  : "jenkins",
                job   : "SPINNAKER-package-pond",
                master: "master1"]],
    stages  : [[type: "bake"],
               [type: "deploy", cluster: [name: "foo"]]]
  ]

  def pipeline3 = [
    name    : "pipeline3",
    triggers: [[type  : "jenkins",
                job   : "SPINNAKER-package-pond",
                master: "master2"]],
    stages  : []
  ]

  void "should pick up stages subsequent to build job completing"() {
    setup:
    PipelineStarter pipelineStarter = Mock(PipelineStarter)
    def handler = new BuildJobNotificationHandler(pipelineStarter: pipelineStarter, objectMapper: new ObjectMapper())
    handler.interestingPipelines[BuildJobNotificationHandler.generateKey(input.master, input.name)] = [pipeline1]

    when:
    handler.handle(input)

    then:
    1 * pipelineStarter.start(_) >> { json ->
      def config = new JsonSlurper().parseText(json) as Map
      assert config.stages.size() == 2
      assert config.stages[0].type == "bake"
      assert config.stages[1].type == "deploy"
      assert config.trigger.type == "jenkins"
      assert config.trigger.buildInfo == input
      def pipeline = new Pipeline()
      pipeline.id = "1"
      return pipeline
    }

    where:
    input = [name: "SPINNAKER-package-pond", master: "master1", lastBuildStatus: "Success"]
  }

  void "should add multiple pipeline targets to single trigger type"() {
    setup:
    def mayo = Mock(MayoService)
    def pipelineStarter = Mock(PipelineStarter)
    def handler = new BuildJobNotificationHandler(pipelineStarter: pipelineStarter, objectMapper: new ObjectMapper(), mayoService: mayo)

    when:
    handler.run()

    then:
    1 * mayo.getPipelines() >> {
      def response = GroovyMock(Response)
      def typedInput = Mock(TypedInput)
      typedInput.in() >> {
        def json = new ObjectMapper().writeValueAsString([pipeline1, pipeline2])
        new ByteArrayInputStream(json.bytes)
      }
      response.getBody() >> typedInput
      response
    }
    2 == handler.interestingPipelines[key].size()
    handler.interestingPipelines[key].name == ["pipeline1", "pipeline2"]

    where:
    key = "master1:SPINNAKER-package-pond"
  }

  void "should only trigger targets from the same master "() {
    given:
    PipelineStarter pipelineStarter = Mock(PipelineStarter)
    def handler = new BuildJobNotificationHandler(pipelineStarter: pipelineStarter, objectMapper: new ObjectMapper())
    handler.interestingPipelines[BuildJobNotificationHandler.generateKey(input.master, input.name)] = [pipeline1]
    handler.interestingPipelines[BuildJobNotificationHandler.generateKey('master2', input.name)] = [pipeline3]

    expect:
    pipeline1.triggers.master != pipeline3.triggers.master

    when:
    handler.handle(input)

    then:
    1 * pipelineStarter.start({
      new JsonSlurper().parseText(it).trigger.master == master
    }) >> rx.Observable.from(pipeline1)

    where:
    master << ['master1', 'master2']
    input = [name: "SPINNAKER-package-pond", master: master, lastBuildStatus: "Success"]

  }

}
