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
import com.google.common.collect.Maps
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.api.ops.AllowLaunchOperation
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

class CreateDeployTaskSpec extends Specification {

  @Subject task = new CreateDeployTask()
  def stage = new Stage(pipeline: new Pipeline(), type: "deploy")
  def mapper = new ObjectMapper()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def deployConfig = [
    application      : "hodor",
    amiName          : "hodor-ubuntu-1",
    instanceType     : "large",
    securityGroups   : ["a", "b", "c"],
    availabilityZones: ["us-east-1": ["a", "d"]],
    capacity         : [
      min    : 1,
      max    : 20,
      desired: 5
    ],
    credentials      : "fzlem"
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper
    task.defaultBakeAccount = "test"

    stage.pipeline.@stages.add(stage)
    stage.context.putAll(deployConfig)
  }

  def cleanup() {
    stage.pipeline.@stages.clear()
    stage.pipeline.@stages.add(stage)
  }

  def "creates a deployment based on job parameters"() {
    given:
    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }
    def expected = Maps.newHashMap(deployConfig)
    expected.with {
      keyPair = 'nf-fzlem-keypair-a'
      securityGroups = securityGroups + ['nf-infrastructure', 'nf-datacenter']
    }

    when:
    task.execute(stage)

    then:
    operations.find {
      it.containsKey("basicAmazonDeployDescription")
    }.basicAmazonDeployDescription == expected
  }

  def "requests an allowLaunch operation for each region"() {
    given:
    deployConfig.availabilityZones["us-west-1"] = []

    and:
    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    with(operations.findAll {
      it.containsKey("allowLaunchDescription")
    }.allowLaunchDescription) { ops ->
      ops.every {
        it instanceof AllowLaunchOperation
      }
      region == deployConfig.availabilityZones.keySet() as List
    }
  }

  def "don't create allowLaunch tasks when in same account"() {
    given:
    task.defaultBakeAccount = 'fzlem'
    deployConfig.availabilityZones["us-west-1"] = []

    and:
    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.findAll { it.containsKey("allowLaunchDescription") }.empty
  }

  def "can include optional parameters"() {
    given:
    stage.context.stack = stackValue
    stage.context.subnetType = subnetTypeValue

    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }
    def expected = [:]

    when:
    task.execute(stage)

    then:
    operations.size() == 2
    operations.find {
      it.containsKey("basicAmazonDeployDescription")
    }.basicAmazonDeployDescription == [
      amiName          : 'hodor-ubuntu-1',
      application      : 'hodor',
      availabilityZones: ['us-east-1': ['a', 'd']],
      capacity         : [min: 1, max: 20, desired: 5],
      credentials      : 'fzlem',
      instanceType     : 'large',
      keyPair          : 'nf-fzlem-keypair-a',
      securityGroups   : ['a', 'b', 'c', 'nf-infrastructure-vpc', 'nf-datacenter-vpc'],
      stack            : 'the-stack-value',
      subnetType       : 'the-subnet-type-value'
    ]

    where:
    stackValue = "the-stack-value"
    subnetTypeValue = "the-subnet-type-value"
  }

  def "can use the AMI output by a bake"() {
    given:
    def operations = []
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations.addAll(it.flatten())
        Observable.from(taskId)
      }
    }
    def bakeStage = new Stage(stage.pipeline, "bake", [ami: amiName])
    stage.pipeline.@stages.clear()
    stage.pipeline.@stages.addAll([bakeStage, stage])

    when:
    task.execute(stage)

    then:
    operations.find {
      it.containsKey("basicAmazonDeployDescription")
    }.basicAmazonDeployDescription.amiName == amiName

    where:
    amiName = "ami-name-from-bake"
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> Observable.from(taskId)
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == PipelineStatus.SUCCEEDED
    result.outputs."kato.task.id" == taskId
  }
}
