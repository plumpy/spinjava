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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject

class ServerGroupCacheForceRefreshTaskSpec extends Specification {

  @Subject task = new ServerGroupCacheForceRefreshTask(objectMapper: new ObjectMapper())
  def stage = new PipelineStage(type: "whatever")

  def deployConfig = [
    "cloudProvider" : "aws",
    "account.name" : "fzlem",
    "deploy.server.groups": ["us-east-1": ["kato-main-v000"]]
  ]

  def setup() {
    stage.context.putAll(deployConfig)
  }

  void "should force cache refresh server groups via oort"() {
    setup:
    task.oort = Mock(OortService)
    Map expectations = [:]

    when:
    def result = task.execute(stage.asImmutable())

    then:
    1 * task.oort.forceCacheUpdate(stage.context.cloudProvider, ServerGroupCacheForceRefreshTask.REFRESH_TYPE, _) >> {
      String cloudProvider, String type, Map<String, ? extends Object> body ->
      expectations = body
      return new Response('oort', 202, 'ok', [], new TypedString("[]"))
    }
    expectations.serverGroupName == (deployConfig."deploy.server.groups"."us-east-1").get(0)
    expectations.account == deployConfig."account.name"
    expectations.region == "us-east-1"
    result.status == ExecutionStatus.RUNNING
  }
}
