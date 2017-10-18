/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.model.Execution.*
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.orchestration
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.net.HttpURLConnection.HTTP_NOT_FOUND

class SpinnakerMetadataServerGroupTagGeneratorSpec extends Specification {
  def oortService = Mock(OortService)
  def retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }

  @Shared
  def notFoundError = RetrofitError.httpError(
    null,
    new Response("http://google.com", HTTP_NOT_FOUND, "Not Found", [], null),
    null,
    null
  )

  @Unroll
  def "should build spinnaker:metadata tag for pipeline"() {
    given:
    def tagGenerator = Spy(SpinnakerMetadataServerGroupTagGenerator, constructorArgs: [oortService, retrySupport]) {
      1 * getPreviousServerGroup(_, _, _, _, _) >> { return previousServerGroup }
    }

    def pipeline = pipeline {
      name = "my pipeline"
      application = "application"
      pipelineConfigId = "configId"
      authentication = authenticatedUser ? new AuthenticationDetails(authenticatedUser) : null

      stage {
        type = "wait"
        context = [comments: "this is a wait stage"]
      }
    }

    when:
    def tags = tagGenerator.generateTags(pipeline.stages[0], "application-v002", "account", "us-west-2", "aws")

    then:
    tags == [[
               name : "spinnaker:metadata",
               value: [
                 executionId     : pipeline.id,
                 pipelineConfigId: "configId",
                 application     : "application",
                 executionType   : "pipeline",
                 description     : "my pipeline",
                 stageId         : pipeline.stages[0].id,
                 comments        : "this is a wait stage",
               ] + (previousServerGroup ? [previousServerGroup: previousServerGroup] : [:])
                 + (authenticatedUser ? [user: authenticatedUser] : [:])
             ]]

    where:
    previousServerGroup                   | authenticatedUser || _
    null                                  | null              || _    // metadata tag should NOT include `previousServerGroup`
    null                                  | "username"        || _    // include user if non-null
    [serverGroupName: "application-v001"] | null              || _
  }

  @Unroll
  def "should build spinnaker:metadata tag for orchestration"() {
    given:
    def tagGenerator = Spy(SpinnakerMetadataServerGroupTagGenerator, constructorArgs: [oortService, retrySupport]) {
      1 * getPreviousServerGroup(_, _, _, _, _) >> { return previousServerGroup }
    }

    def orchestration = orchestration {
      name = "my orchestration"
      application = "application"
      authentication = authenticatedUser ? new AuthenticationDetails(authenticatedUser) : null
      description = "this is my orchestration"

      stages << new Stage<>(delegate, "wait")
    }

    when:
    def tags = tagGenerator.generateTags(orchestration.stages[0], "application-v002", "account", "us-west-2", "aws")

    then:
    tags == [[
               name : "spinnaker:metadata",
               value: [
                 executionId  : orchestration.id,
                 application  : "application",
                 executionType: "orchestration",
                 description  : "this is my orchestration",
                 stageId      : orchestration.stages[0].id,
               ] + (previousServerGroup ? [previousServerGroup: previousServerGroup] : [:])
                 + (authenticatedUser ? [user: authenticatedUser] : [:])
             ]]

    where:
    previousServerGroup                   | authenticatedUser || _
    null                                  | null              || _    // metadata tag should NOT include `previousServerGroup`
    null                                  | "username"        || _    // include user if non-null
    [serverGroupName: "application-v001"] | null              || _
  }

  def "should construct previous server group metadata when present"() {
    given:
    def tagGenerator = new SpinnakerMetadataServerGroupTagGenerator(oortService, retrySupport)

    when: "previous server does exist"
    def previousServerGroupMetadata = tagGenerator.getPreviousServerGroup(
      "application", "account", "cluster", "aws", "us-west-2"
    )

    then: "metadata should be returned"
    1 * oortService.getServerGroupSummary("application", "account", "cluster", "aws", "us-west-2", "ANCESTOR", "image", "true") >> {
      return [
        serverGroupName: "application-v001",
        imageId        : "ami-1234567",
        imageName      : "my_image"
      ]
    }
    previousServerGroupMetadata == [
      name         : "application-v001",
      imageId      : "ami-1234567",
      imageName    : "my_image",
      cloudProvider: "aws"
    ]

    when: "previous server group does NOT exist"
    previousServerGroupMetadata = tagGenerator.getPreviousServerGroup(
      "application", "account", "cluster", "aws", "us-west-2"
    )

    then: "no metadata should be returned"
    1 * oortService.getServerGroupSummary("application", "account", "cluster", "aws", "us-west-2", "ANCESTOR", "image", "true") >> {
      throw notFoundError
    }
    previousServerGroupMetadata == null
  }
}
