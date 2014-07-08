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

package com.netflix.spinnaker.oort.data.aws

import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.spinnaker.oort.data.aws.cachers.AbstractInfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.LoadBalancerCachingAgent
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount

class LoadBalancerCachingAgentSpec extends AbstractCachingAgentSpec {
  @Override
  AbstractInfrastructureCachingAgent getCachingAgent() {
    new LoadBalancerCachingAgent(Mock(AmazonNamedAccount), REGION)
  }

  void "load new load balancers and remove those that have gone missing"() {
    setup:
    def loadBalancerName1 = "kato-main-frontend"
    def loadBalancer1 = new LoadBalancerDescription().withLoadBalancerName(loadBalancerName1)
    def loadBalancerName2 = "kato-main-frontend2"
    def loadBalancer2 = new LoadBalancerDescription().withLoadBalancerName(loadBalancerName2)
    def result = new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancer1, loadBalancer2)

    when:
    agent.load()

    then:
    1 * amazonElasticLoadBalancing.describeLoadBalancers() >> result
    1 * cacheService.put(Keys.getLoadBalancerKey(loadBalancerName1, REGION), loadBalancer1)
    1 * cacheService.put(Keys.getLoadBalancerKey(loadBalancerName2, REGION), loadBalancer2)

    when:
    result.setLoadBalancerDescriptions([loadBalancer1])
    agent.load()

    then:
    1 * amazonElasticLoadBalancing.describeLoadBalancers() >> result
    0 * cacheService.put(_, _)
    1 * cacheService.free(Keys.getLoadBalancerKey(loadBalancerName2, REGION))

    when:
    agent.load()

    then:
    1 * amazonElasticLoadBalancing.describeLoadBalancers() >> result
    0 * cacheService.put(_, _)
    0 * cacheService.free(_)
  }

  void "new load balancer should save to cache"() {
    setup:
    def mocks = getCommonMocks()

    when:
    ((LoadBalancerCachingAgent)agent).loadNewLoadBalancer(mocks.loadBalancer, mocks.region)

    then:
    1 * cacheService.put(Keys.getLoadBalancerKey(mocks.loadBalancerName, mocks.region), mocks.loadBalancer)
  }

  void "missing load balancer should remove from cache"() {
    setup:
    def mocks = getCommonMocks()

    when:
    ((LoadBalancerCachingAgent)agent).removeMissingLoadBalancer(mocks.loadBalancerName, mocks.region)

    then:
    1 * cacheService.free(Keys.getLoadBalancerKey(mocks.loadBalancerName, mocks.region))
  }

  def getCommonMocks() {
    def account = Mock(AmazonNamedAccount)
    account.getName() >> ACCOUNT
    def loadBalancerName = "kato-main-frontend"
    def loadBalancer = new LoadBalancerDescription().withLoadBalancerName(loadBalancerName)
    [account: ACCOUNT, accountObj: account, region: REGION, loadBalancerName: loadBalancerName, loadBalancer: loadBalancer]
  }
}
