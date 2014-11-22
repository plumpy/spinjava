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
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.api.ops.DeleteAmazonLoadBalancerOperation
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

/**
 * Created by aglover on 9/26/14.
 */
class DeleteAmazonLoadBalancerTaskSpec extends Specification {
  @Subject task = new DeleteAmazonLoadBalancerTask()
  def stage = new PipelineStage(type: "whatever")
  def mapper = new ObjectMapper()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def deleteAmazonLoadBalancerConfig = [
    loadBalancerName: "flapjack-frontend",
    regions         : ["us-west-1"],
    credentials     : "fzlem"
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper

    stage.context.putAll(deleteAmazonLoadBalancerConfig)
  }

  def "creates a delete ELB task based on job parameters"() {
    given:
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        rx.Observable.from(taskId)
      }
    }

    when:
    task.execute(stage.asImmutable())

    then:
    operations.size() == 1
    with(operations[0].deleteAmazonLoadBalancerDescription) {
      it instanceof DeleteAmazonLoadBalancerOperation
      loadBalancerName == deleteAmazonLoadBalancerConfig.loadBalancerName
      regions == deleteAmazonLoadBalancerConfig.regions
      credentials == deleteAmazonLoadBalancerConfig.credentials
    }
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> rx.Observable.from(taskId)
    }

    when:
    def result = task.execute(stage.asImmutable())

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.outputs."kato.task.id" == taskId
    result.outputs."delete.account.name" == deleteAmazonLoadBalancerConfig.credentials
  }
}
