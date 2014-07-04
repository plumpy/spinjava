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

package com.netflix.spinnaker.orca.bakery.tasks

import spock.lang.Specification
import spock.lang.Subject
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import rx.Observable
import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.RUNNING
import static java.util.UUID.randomUUID

class CreateBakeTaskSpec extends Specification {

  @Subject task = new CreateBakeTask()
  def context = new SimpleTaskContext()
  def mapper = new ObjectMapper()
  def runningStatus = new BakeStatus(id: randomUUID(), state: RUNNING)

  def bakeConfig = [
      region   : "us-west-1",
      package  : "hodor",
      user     : "bran",
      baseOs   : BakeRequest.OperatingSystem.ubuntu.name(),
      baseLabel: BakeRequest.Label.release.name()
  ]

  def setup() {
    task.mapper = mapper

    bakeConfig.each {
      context."bake.$it.key" = it.value
    }
  }

  def "creates a bake for the correct region"() {
    given:
    task.bakery = Mock(BakeryService)

    when:
    task.execute(context)

    then:
    1 * task.bakery.createBake(bakeConfig.region, _ as BakeRequest) >> Observable.from(runningStatus)
  }

  def "gets bake configuration from job context"() {
    given:
    def bake
    task.bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Observable.from(runningStatus)
      }
    }

    when:
    task.execute(context)

    then:
    bake.user == bakeConfig.user
    bake.packageName == bakeConfig.package
    bake.baseOs.name() == bakeConfig.baseOs
    bake.baseLabel.name() == bakeConfig.baseLabel
  }

  def "outputs the status of the bake"() {
    given:
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }

    when:
    def result = task.execute(context)

    then:
    with(result.outputs["bake.status"]) {
      id == runningStatus.id
      state == runningStatus.state
    }
  }

}
