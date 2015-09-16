/*
 * Copyright 2015 Google, Inc.
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

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting
import com.google.api.client.testing.json.MockJsonFactory
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import com.netflix.spinnaker.kato.gce.deploy.description.UpsertGoogleFirewallRuleDescription
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleFirewallRuleAtomicOperationUnitSpec extends Specification {
  private static final FIREWALL_RULE_NAME = "spinnaker-firewall-1"
  private static final NETWORK_NAME = "default"
  private static final SOURCE_RANGE = "192.0.0.0/8"
  private static final SOURCE_TAG = "some-source-tag"
  private static final IP_PROTOCOL = "tcp"
  private static final PORT_RANGE = "8070-8080"
  private static final TARGET_TAG = "some-target-tag"
  private static final ORIG_TARGET_TAG = "some-other-target-tag"
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should insert new firewall rule with generated target tag"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewallsInsertMock = Mock(Compute.Firewalls.Insert)
      GoogleJsonResponseException notFoundException =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleFirewallRuleDescription(
          firewallRuleName: FIREWALL_RULE_NAME,
          network: NETWORK_NAME,
          sourceRanges: [SOURCE_RANGE],
          sourceTags: [SOURCE_TAG],
          allowed: [
              [
                  ipProtocol: IP_PROTOCOL,
                  portRanges: [PORT_RANGE]
              ]
          ],
          accountName: ACCOUNT_NAME,
          credentials: credentials
      )
      @Subject def operation = new UpsertGoogleFirewallRuleAtomicOperation(description)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, FIREWALL_RULE_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> {throw notFoundException}

      // Insert the new firewall rule.
      1 * firewallsMock.insert(PROJECT_NAME, {
          it.name == FIREWALL_RULE_NAME &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          it.targetTags?.get(0).startsWith("$FIREWALL_RULE_NAME-")
      }) >> firewallsInsertMock
  }

  void "should insert new firewall rule with specified target tag"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewallsInsertMock = Mock(Compute.Firewalls.Insert)
      GoogleJsonResponseException notFoundException =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleFirewallRuleDescription(
          firewallRuleName: FIREWALL_RULE_NAME,
          network: NETWORK_NAME,
          sourceRanges: [SOURCE_RANGE],
          sourceTags: [SOURCE_TAG],
          allowed: [
              [
                  ipProtocol: IP_PROTOCOL,
                  portRanges: [PORT_RANGE]
              ]
          ],
          targetTags: [TARGET_TAG],
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new UpsertGoogleFirewallRuleAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, FIREWALL_RULE_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> {throw notFoundException}

      // Insert the new firewall rule.
      1 * firewallsMock.insert(PROJECT_NAME, {
          it.name == FIREWALL_RULE_NAME &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          it.targetTags == [TARGET_TAG]
      }) >> firewallsInsertMock
  }

  void "should update existing firewall rule and set generated target tag"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewall = new Firewall(name: FIREWALL_RULE_NAME)
      def firewallsUpdateMock = Mock(Compute.Firewalls.Update)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleFirewallRuleDescription(firewallRuleName: FIREWALL_RULE_NAME,
          network: NETWORK_NAME,
          sourceRanges: [SOURCE_RANGE],
          sourceTags: [SOURCE_TAG],
          allowed: [
              [
                  ipProtocol: IP_PROTOCOL,
                  portRanges: [PORT_RANGE]
              ]
          ],
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new UpsertGoogleFirewallRuleAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, FIREWALL_RULE_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> firewall

      // Update the existing firewall rule.
      1 * firewallsMock.update(PROJECT_NAME, FIREWALL_RULE_NAME, {
          it.name == FIREWALL_RULE_NAME &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          it.targetTags?.get(0).startsWith("$FIREWALL_RULE_NAME-")
      }) >> firewallsUpdateMock
  }

  void "should update existing firewall rule and set specified target tag"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewall = new Firewall(name: FIREWALL_RULE_NAME)
      def firewallsUpdateMock = Mock(Compute.Firewalls.Update)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleFirewallRuleDescription(firewallRuleName: FIREWALL_RULE_NAME,
        network: NETWORK_NAME,
        sourceRanges: [SOURCE_RANGE],
        sourceTags: [SOURCE_TAG],
        allowed: [
            [
                ipProtocol: IP_PROTOCOL,
                portRanges: [PORT_RANGE]
            ]
        ],
        targetTags: [TARGET_TAG],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleFirewallRuleAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, FIREWALL_RULE_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> firewall

      // Update the existing firewall rule.
      1 * firewallsMock.update(PROJECT_NAME, FIREWALL_RULE_NAME, {
        it.name == FIREWALL_RULE_NAME &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          it.targetTags == [TARGET_TAG]
      }) >> firewallsUpdateMock
  }

  void "should update existing firewall rule and override existing target tag with specified target tag"() {
    setup:
      def computeMock = Mock(Compute)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsGetMock = Mock(Compute.Firewalls.Get)
      def firewall = new Firewall(name: FIREWALL_RULE_NAME, targetTags: [ORIG_TARGET_TAG])
      def firewallsUpdateMock = Mock(Compute.Firewalls.Update)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new UpsertGoogleFirewallRuleDescription(firewallRuleName: FIREWALL_RULE_NAME,
        network: NETWORK_NAME,
        sourceRanges: [SOURCE_RANGE],
        sourceTags: [SOURCE_TAG],
        allowed: [
            [
                ipProtocol: IP_PROTOCOL,
                portRanges: [PORT_RANGE]
            ]
        ],
        targetTags: [TARGET_TAG],
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new UpsertGoogleFirewallRuleAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the network.
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])

      // Check if the firewall rule exists already.
      2 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.get(PROJECT_NAME, FIREWALL_RULE_NAME) >> firewallsGetMock
      1 * firewallsGetMock.execute() >> firewall

      // Update the existing firewall rule.
      1 * firewallsMock.update(PROJECT_NAME, FIREWALL_RULE_NAME, {
        it.name == FIREWALL_RULE_NAME &&
          it.sourceRanges == [SOURCE_RANGE] &&
          it.sourceTags == [SOURCE_TAG] &&
          it.allowed == [new Firewall.Allowed(IPProtocol: IP_PROTOCOL, ports: [PORT_RANGE])] &&
          it.targetTags == [TARGET_TAG]
      }) >> firewallsUpdateMock
  }
}
