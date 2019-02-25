/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

class CloudFoundryDeployServiceTaskTest extends Specification {
  @Subject task = new CloudFoundryDeployServiceTask(null)

  def "should make a request to clouddriver to deploy a service"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(cloudProvider, [["deployService": context]]) >>
        Observable.from(taskId)
    }

    and:
    def stage = new Stage(Execution.newPipeline("orca"), type, context)

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context == [
      "notification.type": type,
      "kato.last.task.id": taskId,
      "service.region": region,
      "service.account": credentials,
    ]

    where:
    type = "deployService"
    taskIdString = "kato-task-id"
    credentials = "cf-foundation"
    region = "org > space"
    taskId = new TaskId(taskIdString)
    cloudProvider = "my-cloud"
    context = [
      "cloudProvider": cloudProvider,
      "credentials": credentials,
      "region": region
    ]
  }
}
