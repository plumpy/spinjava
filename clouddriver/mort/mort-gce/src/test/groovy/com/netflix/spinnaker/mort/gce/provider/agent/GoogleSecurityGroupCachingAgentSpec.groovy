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

package com.netflix.spinnaker.mort.gce.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Firewall
import com.google.api.services.compute.model.FirewallList
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.mort.gce.cache.Keys
import spock.lang.Specification
import spock.lang.Subject

class GoogleSecurityGroupCachingAgentSpec extends Specification {

  static final String PROJECT_NAME = "my-project"
  static final String REGION = 'global'
  static final String ACCOUNT_NAME = 'some-account-name'

  void "should add security groups on initial run"() {
    setup:
      def googleCloudProvider = new GoogleCloudProvider()
      def computeMock = Mock(Compute)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def firewallsMock = Mock(Compute.Firewalls)
      def firewallsListMock = Mock(Compute.Firewalls.List)
      def securityGroupA = new Firewall(name: 'name-a')
      def securityGroupB = new Firewall(name: 'name-b')
      def keyGroupA = Keys.getSecurityGroupKey(securityGroupA.name, securityGroupA.name, REGION, ACCOUNT_NAME, null)
      def keyGroupB = Keys.getSecurityGroupKey(securityGroupB.name, securityGroupB.name, REGION, ACCOUNT_NAME, null)
      def firewallListReal = new FirewallList(items: [
        securityGroupA,
        securityGroupB
      ])
      def ProviderCache providerCache = Mock(ProviderCache)
      @Subject GoogleSecurityGroupCachingAgent agent = new GoogleSecurityGroupCachingAgent(
        googleCloudProvider, ACCOUNT_NAME, credentials, new ObjectMapper(), Spectator.registry())

    when:
      def cache = agent.loadData(providerCache)

    then:
      1 * computeMock.firewalls() >> firewallsMock
      1 * firewallsMock.list(PROJECT_NAME) >> firewallsListMock
      1 * firewallsListMock.execute() >> firewallListReal
      with (cache.cacheResults.get(Keys.Namespace.SECURITY_GROUPS.ns)) { List<CacheData> cd ->
        cd.size() == 2
        cd.id.containsAll([keyGroupA, keyGroupB])
      }
      0 * _
  }
}
