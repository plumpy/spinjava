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
import com.google.api.services.compute.model.Operation
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEResourceNotFoundException
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleHttpLoadBalancerDescription
import spock.lang.Specification
import spock.lang.Subject

class DeleteGoogleHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final HTTP_LOAD_BALANCER_NAME = "default"
  private static final TARGET_HTTP_PROXY_URL = "project/target-http-proxy"
  private static final TARGET_HTTP_PROXY_NAME = "target-http-proxy"
  private static final URL_MAP_URL = "project/url-map"
  private static final URL_MAP_NAME = "url-map"
  private static final BACKEND_SERVICE_URL = "project/backend-service"
  private static final BACKEND_SERVICE_NAME = "backend-service"
  private static final HEALTH_CHECK_URL = "project/health-check"
  private static final HEALTH_CHECK_NAME = "health-check"
  private static final FORWARDING_RULE_DELETE_OP_NAME = "delete-forwarding-rule"
  private static final TARGET_HTTP_PROXY_DELETE_OP_NAME = "delete-target-http-proxy"
  private static final URL_MAP_DELETE_OP_NAME = "delete-url-map"
  private static final BACKEND_SERVICE_DELETE_OP_NAME = "delete-backend-service"
  private static final HEALTH_CHECK_DELETE_OP_NAME = "delete-health-check"
  private static final PENDING = "PENDING"
  private static final DONE = "DONE"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should delete Http Load Balancer with one backend service"() {
    setup:
      def computeMock = Mock(Compute)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRule = new com.google.api.services.compute.model.ForwardingRule(target: TARGET_HTTP_PROXY_URL)
      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.TargetHttpProxies.Get)
      def targetHttpProxy = new com.google.api.services.compute.model.TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMap = new com.google.api.services.compute.model.UrlMap(defaultService: BACKEND_SERVICE_URL)
      def backendServices = Mock(Compute.BackendServices)
      def backendServicesGet = Mock(Compute.BackendServices.Get)
      def backendService = new com.google.api.services.compute.model.BackendService(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.HttpHealthChecks)

      def globalForwardingRulesDelete = Mock(Compute.GlobalForwardingRules.Delete)
      def globalForwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: DONE)
      def targetHttpProxiesDelete = Mock(Compute.TargetHttpProxies.Delete)
      def targetHttpProxiesDeleteOp = new Operation(
          name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
          status: DONE)
      def urlMapsDelete = Mock(Compute.UrlMaps.Delete)
      def urlMapsDeleteOp = new Operation(
          name: URL_MAP_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete = Mock(Compute.BackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME,
          status: DONE)
      def healthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: DONE)

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalForwardingRulesOperationGet = Mock(Compute.GlobalOperations.Get)
      def targetHttpProxiesOperationGet = Mock(Compute.GlobalOperations.Get)
      def urlMapsOperationGet = Mock(Compute.GlobalOperations.Get)
      def backendServicesOperationGet = Mock(Compute.GlobalOperations.Get)
      def healthChecksOperationGet = Mock(Compute.GlobalOperations.Get)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new DeleteGoogleHttpLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleHttpLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.get(PROJECT_NAME, HTTP_LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> forwardingRule
      2 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.get(PROJECT_NAME, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      1 * targetHttpProxiesGet.execute() >> targetHttpProxy
      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, URL_MAP_NAME) >> urlMapsGet
      1 * urlMapsGet.execute() >> urlMap
      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      1 * globalForwardingRules.delete(PROJECT_NAME, HTTP_LOAD_BALANCER_NAME) >> globalForwardingRulesDelete
      1 * globalForwardingRulesDelete.execute() >> globalForwardingRulesDeleteOp
      1 * targetHttpProxies.delete(PROJECT_NAME, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOp
      1 * urlMaps.delete(PROJECT_NAME, URL_MAP_NAME) >> urlMapsDelete
      1 * urlMapsDelete.execute() >> urlMapsDeleteOp
      1 * backendServices.delete(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp
      1 * computeMock.httpHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp

      5 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, FORWARDING_RULE_DELETE_OP_NAME) >> globalForwardingRulesOperationGet
      1 * globalForwardingRulesOperationGet.execute() >> globalForwardingRulesDeleteOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_DELETE_OP_NAME) >> targetHttpProxiesOperationGet
      1 * targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_DELETE_OP_NAME) >> urlMapsOperationGet
      1 * urlMapsOperationGet.execute() >> urlMapsDeleteOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_DELETE_OP_NAME) >> backendServicesOperationGet
      1 * backendServicesOperationGet.execute() >> backendServicesDeleteOp
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME) >> healthChecksOperationGet
      1 * healthChecksOperationGet.execute() >> healthChecksDeleteOp
  }

  void "should delete Http Load Balancer with multiple backend services/health checks"() {
    setup:
      def computeMock = Mock(Compute)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRule = new com.google.api.services.compute.model.ForwardingRule(target: TARGET_HTTP_PROXY_URL)
      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.TargetHttpProxies.Get)
      def targetHttpProxy = new com.google.api.services.compute.model.TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMap = new com.google.api.services.compute.model.UrlMap(
          defaultService: BACKEND_SERVICE_URL,
          pathMatchers: [
              [defaultService: BACKEND_SERVICE_URL+"2",
               pathRules: [
                  [service: BACKEND_SERVICE_URL+"3"], [service: BACKEND_SERVICE_URL]
               ]]
          ])
      def backendServices = Mock(Compute.BackendServices)
      def backendServicesGet = Mock(Compute.BackendServices.Get)
      def backendServicesGet2 = Mock(Compute.BackendServices.Get)
      def backendServicesGet3 = Mock(Compute.BackendServices.Get)
      def backendService = new com.google.api.services.compute.model.BackendService(healthChecks: [HEALTH_CHECK_URL])
      def backendService2 = new com.google.api.services.compute.model.BackendService(healthChecks: [HEALTH_CHECK_URL+"2"])
      def backendService3 = new com.google.api.services.compute.model.BackendService(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.HttpHealthChecks)

      def globalForwardingRulesDelete = Mock(Compute.GlobalForwardingRules.Delete)
      def globalForwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: DONE)
      def targetHttpProxiesDelete = Mock(Compute.TargetHttpProxies.Delete)
      def targetHttpProxiesDeleteOp = new Operation(
          name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
          status: DONE)
      def urlMapsDelete = Mock(Compute.UrlMaps.Delete)
      def urlMapsDeleteOp = new Operation(
          name: URL_MAP_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete = Mock(Compute.BackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete2 = Mock(Compute.BackendServices.Delete)
      def backendServicesDeleteOp2 = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME+"2",
          status: DONE)
      def backendServicesDelete3 = Mock(Compute.BackendServices.Delete)
      def backendServicesDeleteOp3 = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME+"3",
          status: DONE)
      def healthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: DONE)
      def healthChecksDelete2 = Mock(Compute.HttpHealthChecks.Delete)
      def healthChecksDeleteOp2 = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME+"2",
          status: DONE)

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalForwardingRulesOperationGet = Mock(Compute.GlobalOperations.Get)
      def targetHttpProxiesOperationGet = Mock(Compute.GlobalOperations.Get)
      def urlMapsOperationGet = Mock(Compute.GlobalOperations.Get)
      def backendServicesOperationGet = Mock(Compute.GlobalOperations.Get)
      def backendServicesOperationGet2 = Mock(Compute.GlobalOperations.Get)
      def backendServicesOperationGet3 = Mock(Compute.GlobalOperations.Get)
      def healthChecksOperationGet = Mock(Compute.GlobalOperations.Get)
      def healthChecksOperationGet2 = Mock(Compute.GlobalOperations.Get)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new DeleteGoogleHttpLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleHttpLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.get(PROJECT_NAME, HTTP_LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> forwardingRule
      2 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.get(PROJECT_NAME, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      1 * targetHttpProxiesGet.execute() >> targetHttpProxy
      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, URL_MAP_NAME) >> urlMapsGet
      1 * urlMapsGet.execute() >> urlMap

      6 * computeMock.backendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService
      1 * backendServices.get(PROJECT_NAME, BACKEND_SERVICE_NAME+"2") >> backendServicesGet2
      1 * backendServicesGet2.execute() >> backendService2
      1 * backendServices.get(PROJECT_NAME, BACKEND_SERVICE_NAME+"3") >> backendServicesGet3
      1 * backendServicesGet3.execute() >> backendService3

      1 * globalForwardingRules.delete(PROJECT_NAME, HTTP_LOAD_BALANCER_NAME) >> globalForwardingRulesDelete
      1 * globalForwardingRulesDelete.execute() >> globalForwardingRulesDeleteOp
      1 * targetHttpProxies.delete(PROJECT_NAME, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOp
      1 * urlMaps.delete(PROJECT_NAME, URL_MAP_NAME) >> urlMapsDelete
      1 * urlMapsDelete.execute() >> urlMapsDeleteOp

      1 * backendServices.delete(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp
      1 * backendServices.delete(PROJECT_NAME, BACKEND_SERVICE_NAME+"2") >> backendServicesDelete2
      1 * backendServicesDelete2.execute() >> backendServicesDeleteOp2
      1 * backendServices.delete(PROJECT_NAME, BACKEND_SERVICE_NAME+"3") >> backendServicesDelete3
      1 * backendServicesDelete3.execute() >> backendServicesDeleteOp3
      2 * computeMock.httpHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME+"2") >> healthChecksDelete2
      1 * healthChecksDelete2.execute() >> healthChecksDeleteOp2

      8 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, FORWARDING_RULE_DELETE_OP_NAME) >> globalForwardingRulesOperationGet
      1 * globalForwardingRulesOperationGet.execute() >> globalForwardingRulesDeleteOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_DELETE_OP_NAME) >> targetHttpProxiesOperationGet
      1 * targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_DELETE_OP_NAME) >> urlMapsOperationGet
      1 * urlMapsOperationGet.execute() >> urlMapsDeleteOp

      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_DELETE_OP_NAME) >> backendServicesOperationGet
      1 * backendServicesOperationGet.execute() >> backendServicesDeleteOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_DELETE_OP_NAME+"2") >> backendServicesOperationGet2
      1 * backendServicesOperationGet2.execute() >> backendServicesDeleteOp2
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_DELETE_OP_NAME+"3") >> backendServicesOperationGet3
      1 * backendServicesOperationGet3.execute() >> backendServicesDeleteOp3
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME) >> healthChecksOperationGet
      1 * healthChecksOperationGet.execute() >> healthChecksDeleteOp
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME+"2") >> healthChecksOperationGet2
      1 * healthChecksOperationGet2.execute() >> healthChecksDeleteOp2
  }

  void "should fail to delete an Http Load Balancer that does not exist"() {
    setup:
      def computeMock = Mock(Compute)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new DeleteGoogleHttpLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleHttpLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.get(PROJECT_NAME, HTTP_LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> null
      thrown GCEResourceNotFoundException
  }

  void "should fail to delete Http Load Balancer if failed to delete a resource"() {
    setup:
      def computeMock = Mock(Compute)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRule = new com.google.api.services.compute.model.ForwardingRule(target: TARGET_HTTP_PROXY_URL)
      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.TargetHttpProxies.Get)
      def targetHttpProxy = new com.google.api.services.compute.model.TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMap = new com.google.api.services.compute.model.UrlMap(defaultService: BACKEND_SERVICE_URL)
      def backendServices = Mock(Compute.BackendServices)
      def backendServicesGet = Mock(Compute.BackendServices.Get)
      def backendService = new com.google.api.services.compute.model.BackendService(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.HttpHealthChecks)

      def globalForwardingRulesDelete = Mock(Compute.GlobalForwardingRules.Delete)
      def globalForwardingRulesDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: DONE)
      def targetHttpProxiesDelete = Mock(Compute.TargetHttpProxies.Delete)
      def targetHttpProxiesDeleteOp = new Operation(
          name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
          status: DONE)
      def urlMapsDelete = Mock(Compute.UrlMaps.Delete)
      def urlMapsDeleteOp = new Operation(
          name: URL_MAP_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete = Mock(Compute.BackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME,
          status: DONE)
      def healthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def healthChecksPendingDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: PENDING)
      def healthChecksFailingDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: DONE,
          error: new Operation.Error(errors: [new Operation.Error.Errors(message: "error")]))

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalForwardingRulesOperationGet = Mock(Compute.GlobalOperations.Get)
      def targetHttpProxiesOperationGet = Mock(Compute.GlobalOperations.Get)
      def urlMapsOperationGet = Mock(Compute.GlobalOperations.Get)
      def backendServicesOperationGet = Mock(Compute.GlobalOperations.Get)
      def healthChecksOperationGet = Mock(Compute.GlobalOperations.Get)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new DeleteGoogleHttpLoadBalancerDescription(
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleHttpLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.get(PROJECT_NAME, HTTP_LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> forwardingRule
      2 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.get(PROJECT_NAME, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      1 * targetHttpProxiesGet.execute() >> targetHttpProxy
      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, URL_MAP_NAME) >> urlMapsGet
      1 * urlMapsGet.execute() >> urlMap
      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      1 * globalForwardingRules.delete(PROJECT_NAME, HTTP_LOAD_BALANCER_NAME) >> globalForwardingRulesDelete
      1 * globalForwardingRulesDelete.execute() >> globalForwardingRulesDeleteOp
      1 * targetHttpProxies.delete(PROJECT_NAME, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOp
      1 * urlMaps.delete(PROJECT_NAME, URL_MAP_NAME) >> urlMapsDelete
      1 * urlMapsDelete.execute() >> urlMapsDeleteOp
      1 * backendServices.delete(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp
      1 * computeMock.httpHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksPendingDeleteOp

      5 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, FORWARDING_RULE_DELETE_OP_NAME) >> globalForwardingRulesOperationGet
      1 * globalForwardingRulesOperationGet.execute() >> globalForwardingRulesDeleteOp
      1 * globalOperations.get(PROJECT_NAME, TARGET_HTTP_PROXY_DELETE_OP_NAME) >> targetHttpProxiesOperationGet
      1 * targetHttpProxiesOperationGet.execute() >> targetHttpProxiesDeleteOp
      1 * globalOperations.get(PROJECT_NAME, URL_MAP_DELETE_OP_NAME) >> urlMapsOperationGet
      1 * urlMapsOperationGet.execute() >> urlMapsDeleteOp
      1 * globalOperations.get(PROJECT_NAME, BACKEND_SERVICE_DELETE_OP_NAME) >> backendServicesOperationGet
      1 * backendServicesOperationGet.execute() >> backendServicesDeleteOp
      1 * globalOperations.get(PROJECT_NAME, HEALTH_CHECK_DELETE_OP_NAME) >> healthChecksOperationGet
      1 * healthChecksOperationGet.execute() >> healthChecksFailingDeleteOp
      thrown GCEResourceNotFoundException
  }

  void "should fail to delete Http Load Balancer if timed out while deleting a resource"() {
    setup:
      def computeMock = Mock(Compute)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesGet = Mock(Compute.GlobalForwardingRules.Get)
      def forwardingRule = new com.google.api.services.compute.model.ForwardingRule(target: TARGET_HTTP_PROXY_URL)
      def targetHttpProxies = Mock(Compute.TargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.TargetHttpProxies.Get)
      def targetHttpProxy = new com.google.api.services.compute.model.TargetHttpProxy(urlMap: URL_MAP_URL)
      def urlMaps = Mock(Compute.UrlMaps)
      def urlMapsGet = Mock(Compute.UrlMaps.Get)
      def urlMap = new com.google.api.services.compute.model.UrlMap(defaultService: BACKEND_SERVICE_URL)
      def backendServices = Mock(Compute.BackendServices)
      def backendServicesGet = Mock(Compute.BackendServices.Get)
      def backendService = new com.google.api.services.compute.model.BackendService(healthChecks: [HEALTH_CHECK_URL])
      def healthChecks = Mock(Compute.HttpHealthChecks)

      def globalForwardingRulesDelete = Mock(Compute.GlobalForwardingRules.Delete)
      def globalForwardingRulesPendingDeleteOp = new Operation(
          name: FORWARDING_RULE_DELETE_OP_NAME,
          status: PENDING)
      def targetHttpProxiesDelete = Mock(Compute.TargetHttpProxies.Delete)
      def targetHttpProxiesDeleteOp = new Operation(
          name: TARGET_HTTP_PROXY_DELETE_OP_NAME,
          status: DONE)
      def urlMapsDelete = Mock(Compute.UrlMaps.Delete)
      def urlMapsDeleteOp = new Operation(
          name: URL_MAP_DELETE_OP_NAME,
          status: DONE)
      def backendServicesDelete = Mock(Compute.BackendServices.Delete)
      def backendServicesDeleteOp = new Operation(
          name: BACKEND_SERVICE_DELETE_OP_NAME,
          status: DONE)
      def healthChecksDelete = Mock(Compute.HttpHealthChecks.Delete)
      def healthChecksDeleteOp = new Operation(
          name: HEALTH_CHECK_DELETE_OP_NAME,
          status: DONE)

      def globalOperations = Mock(Compute.GlobalOperations)
      def globalForwardingRulesOperationGet = Mock(Compute.GlobalOperations.Get)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new DeleteGoogleHttpLoadBalancerDescription(
          deleteOperationTimeoutSeconds: 0,
          loadBalancerName: HTTP_LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new DeleteGoogleHttpLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      2 * computeMock.globalForwardingRules() >> globalForwardingRules
      1 * globalForwardingRules.get(PROJECT_NAME, HTTP_LOAD_BALANCER_NAME) >> globalForwardingRulesGet
      1 * globalForwardingRulesGet.execute() >> forwardingRule
      2 * computeMock.targetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.get(PROJECT_NAME, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesGet
      1 * targetHttpProxiesGet.execute() >> targetHttpProxy
      2 * computeMock.urlMaps() >> urlMaps
      1 * urlMaps.get(PROJECT_NAME, URL_MAP_NAME) >> urlMapsGet
      1 * urlMapsGet.execute() >> urlMap
      2 * computeMock.backendServices() >> backendServices
      1 * backendServices.get(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesGet
      1 * backendServicesGet.execute() >> backendService

      1 * globalForwardingRules.delete(PROJECT_NAME, HTTP_LOAD_BALANCER_NAME) >> globalForwardingRulesDelete
      1 * globalForwardingRulesDelete.execute() >> globalForwardingRulesPendingDeleteOp
      1 * targetHttpProxies.delete(PROJECT_NAME, TARGET_HTTP_PROXY_NAME) >> targetHttpProxiesDelete
      1 * targetHttpProxiesDelete.execute() >> targetHttpProxiesDeleteOp
      1 * urlMaps.delete(PROJECT_NAME, URL_MAP_NAME) >> urlMapsDelete
      1 * urlMapsDelete.execute() >> urlMapsDeleteOp
      1 * backendServices.delete(PROJECT_NAME, BACKEND_SERVICE_NAME) >> backendServicesDelete
      1 * backendServicesDelete.execute() >> backendServicesDeleteOp
      1 * computeMock.httpHealthChecks() >> healthChecks
      1 * healthChecks.delete(PROJECT_NAME, HEALTH_CHECK_NAME) >> healthChecksDelete
      1 * healthChecksDelete.execute() >> healthChecksDeleteOp

      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, FORWARDING_RULE_DELETE_OP_NAME) >> globalForwardingRulesOperationGet
      1 * globalForwardingRulesOperationGet.execute() >> globalForwardingRulesPendingDeleteOp
      thrown GCEResourceNotFoundException
  }
}
