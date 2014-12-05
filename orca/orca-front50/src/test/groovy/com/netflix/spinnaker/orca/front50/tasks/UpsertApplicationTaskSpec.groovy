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


package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.model.Front50Credential
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class UpsertApplicationTaskSpec extends Specification {
  @Subject
  def task = new UpsertApplicationTask(mapper: new ObjectMapper())

  def config = [
    account    : "test",
    application: [
      "name" : "application",
      "owner": "owner"
    ]
  ]

  def globalAccount = "global"

  void "should not create a global application if no global credentials are available"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get(config.account, config.application.name) >> null
      1 * getCredentials() >> []
      1 * create(config.account, config.application.name, {
        it.properties == new Application(config.application).properties
      })
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "should create global and non-global applications"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get(globalAccount, config.application.name) >> null
      1 * get(config.account, config.application.name) >> null
      1 * getCredentials() >> [new Front50Credential(name: globalAccount, global: true)]
      1 * create(globalAccount, config.application.name, {
        it.properties == new Application(config.application + [accounts: config.account]).properties
      })
      1 * create(config.account, config.application.name, {
        it.properties == new Application(config.application).properties
      })
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "should update existing global application and create new non-global application"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get(globalAccount, config.application.name) >> existingGlobalApplication
      1 * get(config.account, config.application.name) >> null
      1 * getCredentials() >> [new Front50Credential(name: globalAccount, global: true)]
      1 * update(globalAccount, {
        // assert that the global application is updated w/ new application attributes and merged accounts
        it.properties == new Application(config.application + [accounts: "prod,test"]).properties
      })
      1 * create(config.account, config.application.name, {
        // assert that the new application properties override whatever existed in the global registry
        it.properties == (existingGlobalApplication.properties + config.application)
      })
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED

    where:
    existingGlobalApplication = new Application(
      name: "application", owner: "another owner", description: "Description", accounts: "prod"
    )
  }

  void "should update global and non-global applications"() {
    given:
    task.front50Service = Mock(Front50Service) {
      1 * get(globalAccount, config.application.name) >> new Application(accounts: "prod")
      1 * get(config.account, config.application.name) >> new Application()
      1 * getCredentials() >> [new Front50Credential(name: globalAccount, global: true)]
      1 * update(globalAccount, {
        it.properties == new Application(config.application + [accounts: "prod,test"]).properties
      })
      1 * update(config.account, {
        it.properties == new Application(config.application).properties
      })
      0 * _._
    }

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "UpsertApplication", config))

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }
}
