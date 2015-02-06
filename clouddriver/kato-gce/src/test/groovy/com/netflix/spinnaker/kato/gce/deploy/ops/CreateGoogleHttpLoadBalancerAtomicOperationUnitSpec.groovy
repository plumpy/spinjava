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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.Compute
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.kato.gce.deploy.CreateGoogleHttpLoadBalancerTestConstants.*

class CreateGoogleHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final PROJECT_NAME = "my_project"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should create an HTTP Load Balancer with path matcher and backend"() {
    setup:
      def computeMock = Mock(Compute)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new com.google.api.services.compute.model.Operation(targetLink: "health-check")
      def backendServices = Mock(Compute.BackendServices)
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new com.google.api.services.compute.model.Operation(targetLink: "backend-service")
      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapsInsertOp = new com.google.api.services.compute.model.Operation(targetLink: "url-map")
      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.TargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new com.google.api.services.compute.model.Operation(targetLink: "target-proxy")
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
      def insertOp = new com.google.api.services.compute.model.Operation(targetLink: "link")
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new CreateGoogleHttpLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          credentials: credentials,
          healthCheck: [checkIntervalSec: CHECK_INTERVAL_SEC],
          backends: [[group: INSTANCE_GROUP]],
          ipAddress: IP_ADDRESS,
          portRange: PORT_RANGE,
          hostRules: [[hosts: [HOST], pathMatcher: MATCHER]],
          pathMatchers: [[name: MATCHER, defaultService: SERVICE, pathRules: [[paths: [PATH], service: SERVICE]]]]
      )
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)

    when:
     operation.operate([])

    then:
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.insert(PROJECT_NAME, {it.checkIntervalSec == CHECK_INTERVAL_SEC && it.port == null}) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp
      1 * computeMock.backendServices() >> backendServices

      1 * backendServices.insert(PROJECT_NAME,
          {it.backends.size() == 1 && it.backends.get(0).group == INSTANCE_GROUP && it.healthChecks.size() == 1 &&
           it.healthChecks.get(0) == httpHealthChecksInsertOp.targetLink}) >> backendServicesInsert

      1 * backendServicesInsert.execute() >> backendServicesInsertOp
      1 * computeMock.urlMaps() >> urlMaps

      1 * urlMaps.insert(PROJECT_NAME,
          {it.pathMatchers.size() == 1 && it.pathMatchers.get(0).defaultService == SERVICE &&
           it.pathMatchers.get(0).pathRules.size() == 1 && it.pathMatchers.get(0).pathRules.get(0).service == SERVICE &&
           it.pathMatchers.get(0).pathRules.get(0).paths.size() == 1 && it.pathMatchers.get(0).pathRules.get(0).paths.get(0) == PATH &&
           it.defaultService == backendServicesInsertOp.targetLink}) >> urlMapsInsert

      1 * urlMapsInsert.execute() >> urlMapsInsertOp
      1 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp
      1 * computeMock.globalForwardingRules() >> globalForwardingRules

      1 * globalForwardingRules.insert(PROJECT_NAME,
          {it.iPAddress == IP_ADDRESS && it.portRange == PORT_RANGE && it.name == LOAD_BALANCER_NAME
           it.target == targetHttpProxiesInsertOp.targetLink}) >> globalForwardingRulesInsert

      1 * globalForwardingRulesInsert.execute() >> insertOp
  }

  void "should create an HTTP Load Balancer with minimal description"() {
    setup:
      def computeMock = Mock(Compute)
      def httpHealthChecks = Mock(Compute.HttpHealthChecks)
      def httpHealthChecksInsert = Mock(Compute.HttpHealthChecks.Insert)
      def httpHealthChecksInsertOp = new com.google.api.services.compute.model.Operation(targetLink: "health-check")
      def backendServices = Mock(Compute.BackendServices)
      def backendServicesInsert = Mock(Compute.BackendServices.Insert)
      def backendServicesInsertOp = new com.google.api.services.compute.model.Operation(targetLink: "backend-service")
      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsInsert = Mock(Compute.UrlMaps.Insert)
      def urlMapsInsertOp = new com.google.api.services.compute.model.Operation(targetLink: "url-map")
      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesInsert = Mock(Compute.TargetHttpProxies.Insert)
      def targetHttpProxiesInsertOp = new com.google.api.services.compute.model.Operation(targetLink: "target-proxy")
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesInsert = Mock(Compute.GlobalForwardingRules.Insert)
      def insertOp = new com.google.api.services.compute.model.Operation(targetLink: "link")
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new CreateGoogleHttpLoadBalancerDescription(
          loadBalancerName: LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          credentials: credentials,
      )
      @Subject def operation = new CreateGoogleHttpLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.httpHealthChecks() >> httpHealthChecks
      1 * httpHealthChecks.insert(PROJECT_NAME, {it.port == null}) >> httpHealthChecksInsert
      1 * httpHealthChecksInsert.execute() >> httpHealthChecksInsertOp
      1 * computeMock.backendServices() >> backendServices
      1 * backendServices.insert(PROJECT_NAME,
          {it.healthChecks.size() == 1 && it.healthChecks.get(0) == httpHealthChecksInsertOp.targetLink}) >> backendServicesInsert
      1 * backendServicesInsert.execute() >> backendServicesInsertOp
      1 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.insert(PROJECT_NAME, {it.defaultService == backendServicesInsertOp.targetLink}) >> urlMapsInsert
      1 * urlMapsInsert.execute() >> urlMapsInsertOp
      1 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.insert(PROJECT_NAME, {it.urlMap == urlMapsInsertOp.targetLink}) >> targetHttpProxiesInsert
      1 * targetHttpProxiesInsert.execute() >> targetHttpProxiesInsertOp
      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.insert(PROJECT_NAME,
          {it.name == LOAD_BALANCER_NAME && it.target == targetHttpProxiesInsertOp.targetLink}) >> globalForwardingRulesInsert
      1 * globalForwardingRulesInsert.execute() >> insertOp
  }
}
