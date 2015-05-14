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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class QuickPatchStageSpec extends Specification {

  @Subject quickPatchStage = new QuickPatchStage()
  def oort = Mock(OortService)
  def bulkQuickPatchStage = Spy(BulkQuickPatchStage)

  def objectMapper = new OrcaObjectMapper()
  def pipelineStore = new InMemoryPipelineStore(objectMapper)
  def orchestrationStore = new InMemoryOrchestrationStore(objectMapper)
  def executionRepository = new DefaultExecutionRepository(orchestrationStore, pipelineStore)

  void setup() {
    quickPatchStage.applicationContext = Stub(ApplicationContext) {
      getBean(_) >> { Class type -> type.newInstance() }
    }
    quickPatchStage.objectMapper = objectMapper
    quickPatchStage.steps = new StepBuilderFactory(Stub(JobRepository), Stub(PlatformTransactionManager))
    quickPatchStage.taskTaskletAdapter = new TaskTaskletAdapter(executionRepository, [])
    quickPatchStage.oortService = oort
    quickPatchStage.bulkQuickPatchStage = bulkQuickPatchStage
  }

  @Unroll
  def "quick patch can't run due to too many asgs"() {
    given:
    def config = [
      application: "deck",
      cluster: "deck-cluster",
      account: "account",
      region: "us-east-1"
    ]

    and:
    def stage = new PipelineStage(null, "quickPatch", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    quickPatchStage.buildSteps(stage)

    then:
    1 * oort.getCluster(_,_,_,_) >> {
      def responseBody = [
        serverGroups: asgNames.collect { name ->
          [name: name, region: "us-east-1"]
        }
      ]
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          objectMapper.writeValueAsBytes(responseBody)
        )
      )
    }

    and:
    thrown(RuntimeException)

    where:
    asgNames = ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"]
  }

  @Unroll
  def "configures bulk quickpatch"() {
    given:
    def config = [
        application: "deck",
        cluster: "deck-cluster",
        account: "account",
        region: "us-east-1"
    ]

    and:
    def stage = new PipelineStage(null, "quickPatch", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    quickPatchStage.buildSteps(stage)

    then:
    1 * oort.getCluster(_,_,_,_) >> {
      def responseBody = [
          serverGroups: asgNames.collect { name ->
            [name: name, region: "us-east-1", instances: [ instance1, instance2]]
          }
      ]
      new Response(
          "foo", 200, "ok", [],
          new TypedByteArray(
              "application/json",
              objectMapper.writeValueAsBytes(responseBody)
          )
      )
    }

    and:
    1 == stage.afterStages.size()

    and:
    stage.afterStages*.stageBuilder.unique() == [bulkQuickPatchStage]

    and:
    with(stage.afterStages[0].context) {
      application == "deck"
      account == "account"
      region == "us-east-1"
      cluster == "deck-cluster"
      instanceIds == ["i-1234", "i-2345"]
      //instances == expectedInstances
      instances.size() == expectedInstances.size()
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheck == expectedInstances.get(it.key).healthCheck
      }
    }

    where:
    asgNames = ["deck-prestaging-v300"]
    instance1 = [instanceId : "i-1234", publicDnsName : "foo.com", health : [ [foo : "bar"], [ healthCheckUrl : "http://foo.com:7001/healthCheck"] ]]
    instance2 = [instanceId : "i-2345", publicDnsName : "foo2.com", health : [ [foo2 : "bar"], [ healthCheckUrl : "http://foo2.com:7001/healthCheck"] ]]
    expectedInstances = ["i-1234" : [hostName : "foo.com", healthCheckUrl : "http://foo.com:7001/healthCheck"], "i-2345" : [hostName : "foo2.com", healthCheckUrl : "http://foo.com:7001/healthCheck" ] ]
  }


  def "configures rolling quickpatch"() {
    given:
    def config = [
      application: "deck",
      cluster: "deck-cluster",
      account: "account",
      region: "us-east-1",
      rollingPatch: true
    ]

    and:
    def stage = new PipelineStage(null, "quickPatch", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    quickPatchStage.buildSteps(stage)

    then:
    1 * oort.getCluster(_,_,_,_) >> {
      def responseBody = [
        serverGroups: asgNames.collect { name ->
          [name: name, region: "us-east-1", instances: [instance1, instance2]]
        }
      ]
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          objectMapper.writeValueAsBytes(responseBody)
        )
      )
    }

    and:
    2 == stage.afterStages.size()

    and:
    stage.afterStages*.stageBuilder.unique() == [bulkQuickPatchStage]

    and:
    with(stage.afterStages[0].context) {
      application == "deck"
      account == "account"
      region == "us-east-1"
      cluster == "deck-cluster"
      instanceIds == ["i-1234"]
      instances.size() == 1
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheck == expectedInstances.get(it.key).healthCheck
      }
    }

    and:
    with(stage.afterStages[1].context) {
      application == "deck"
      account == "account"
      region == "us-east-1"
      cluster == "deck-cluster"
      instanceIds == ["i-2345"]
      instances.size() == 1
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheck == expectedInstances.get(it.key).healthCheck
      }
    }

    where:
    asgNames = ["deck-prestaging-v300"]
    instance1 = [instanceId : "i-1234", publicDnsName : "foo.com", health : [ [foo : "bar"], [ healthCheckUrl : "http://foo.com:7001/healthCheck"] ]]
    instance2 = [instanceId : "i-2345", publicDnsName : "foo2.com", health : [ [foo2 : "bar"], [ healthCheckUrl : "http://foo2.com:7001/healthCheck"] ]]
    expectedInstances = ["i-1234" : [hostName : "foo.com", healthCheckUrl : "http://foo.com:7001/healthCheck"], "i-2345" : [hostName : "foo2.com", healthCheckUrl : "http://foo.com:7001/healthCheck" ] ]
  }
}
