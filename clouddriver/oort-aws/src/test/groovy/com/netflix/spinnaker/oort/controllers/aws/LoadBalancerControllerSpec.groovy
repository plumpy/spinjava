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

package com.netflix.spinnaker.oort.controllers.aws

import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import spock.lang.Shared
import spock.lang.Specification

class LoadBalancerControllerSpec extends Specification {

  @Shared
  CacheService cacheService

  @Shared
  LoadBalancerController controller

  def setup() {
    cacheService = Mock(CacheService)
    controller = new LoadBalancerController(cacheService: cacheService)
  }

  void "should list all available load balancers from cache"() {
    setup:
    def elb = Mock(LoadBalancerDescription)
    elb.getLoadBalancerName() >> "foo"

    when:
    def resp = controller.list()

    then:
    resp.size() == 1
    resp[0].name == "foo"
    1 * cacheService.keysByType(Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS) >> ["${Keys.Namespace.LOAD_BALANCERS}:foo:test:us-west-1:asg-v001"]
    1 * cacheService.retrieve(Keys.getLoadBalancerKey("foo", "test", "us-west-1"), LoadBalancerDescription) >> elb
  }

  void "should retrieve a specific load balancer by name"() {
    setup:
    def elb = Mock(LoadBalancerDescription)
    elb.getLoadBalancerName() >> "foo"

    when:
    def resp = controller.get("foo")

    then:
    resp.name == "foo"
    1 * cacheService.keysByType(Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS) >> ["${Keys.Namespace.LOAD_BALANCERS}:foo:test:us-west-1:asg-v001".toString()]
    1 * cacheService.retrieve(Keys.getLoadBalancerKey("foo", "test", "us-west-1"), LoadBalancerDescription) >> elb
  }
}
