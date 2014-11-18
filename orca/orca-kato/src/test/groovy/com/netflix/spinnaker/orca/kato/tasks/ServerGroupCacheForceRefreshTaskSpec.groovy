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

import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class ServerGroupCacheForceRefreshTaskSpec extends Specification {

  @Subject task = new ServerGroupCacheForceRefreshTask()
  def stage = new Stage(type: "whatever")

  def deployConfig = [
    "account.name" : "fzlem",
    "server.groups": ["us-east-1": ["kato-main-v000"]]
  ]

  def setup() {
    stage.context.putAll(deployConfig)
  }

  void "should force cache refresh server groups via oort"() {
    setup:
    task.oort = Mock(OortService)

    when:
    task.execute(stage)

    then:
    1 * task.oort.forceCacheUpdate(ServerGroupCacheForceRefreshTask.REFRESH_TYPE, _) >> { String type, Map<String, ? extends Object> body ->
      assert body.asgName == (deployConfig."server.groups"."us-east-1").get(0)
      assert body.account == deployConfig."account.name"
      assert body.region == "us-east-1"
    }
  }
}
