/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.gate

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext
import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.ExecutionHistoryService
import com.netflix.spinnaker.gate.services.internal.OrcaService
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionHistoryServiceSpec extends Specification {
  @Unroll
  def "should fall back to last known good result if Orca times out getting #list"() {
    setup:
    HystrixRequestContext.initializeContext()

    def service = new ExecutionHistoryService()
    service.orcaService = Mock(OrcaService)

    and:
    service.orcaService."$methodName"(app) >> [] >> { throw new SocketTimeoutException() }

    expect:
    service."$methodName"(app) == []

    and:
    service."$methodName"(app) == []

    where:
    app = "bivl"
    list << ["tasks", "pipelines"]
    methodName = "get" + list.capitalize()
  }
}
