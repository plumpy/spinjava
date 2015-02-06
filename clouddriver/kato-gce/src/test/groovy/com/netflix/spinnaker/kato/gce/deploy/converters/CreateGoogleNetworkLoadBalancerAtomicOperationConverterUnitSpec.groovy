/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.gce.deploy.ops.CreateGoogleNetworkLoadBalancerAtomicOperation
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification

class CreateGoogleNetworkLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  private static final NETWORK_LOAD_BALANCER_NAME = "spinnaker-test-v000"
  private static final REGION = "us-central1"
  private static final ACCOUNT_NAME = "auto"
  private static final CHECK_INTERVAL_SEC = 7
  private static final INSTANCE = "inst"
  private static final IP_ADDRESS = "1.1.1.1"
  private static final PORT_RANGE = "80-82"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  CreateGoogleNetworkLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CreateGoogleNetworkLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "createGoogleNetworkLoadBalancerDescription type returns CreateGoogleNetworkLoadBalancerDescription and CreateGoogleNetworkLoadBalancerAtomicOperation"() {
    setup:
      def input = [
          networkLoadBalancerName: NETWORK_LOAD_BALANCER_NAME,
          region: REGION,
          accountName: ACCOUNT_NAME,
          healthCheck: [checkIntervalSec: CHECK_INTERVAL_SEC],
          instances: [INSTANCE],
          ipAddress: IP_ADDRESS,
          portRange: PORT_RANGE
      ]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof CreateGoogleNetworkLoadBalancerDescription
      description.networkLoadBalancerName == NETWORK_LOAD_BALANCER_NAME
      description.healthCheck.checkIntervalSec == 7
      description.healthCheck.timeoutSec == null
      description.instances.size() == 1
      description.instances.get(0) == INSTANCE
      description.ipAddress == IP_ADDRESS
      description.portRange == PORT_RANGE

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof CreateGoogleNetworkLoadBalancerAtomicOperation
  }
}
