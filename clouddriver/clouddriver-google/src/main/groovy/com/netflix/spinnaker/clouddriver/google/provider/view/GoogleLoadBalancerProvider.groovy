/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.LOAD_BALANCERS

@ConditionalOnMissingClass(com.netflix.spinnaker.clouddriver.google.model.GoogleLoadBalancerProvider.class)
@Component
class GoogleLoadBalancerProvider implements LoadBalancerProvider<GoogleLoadBalancer> {

  @Autowired
  final GoogleCloudProvider googleCloudProvider
  @Autowired
  final Cache cacheView
  @Autowired
  final ObjectMapper objectMapper

  @Override
  Set<GoogleLoadBalancer> getApplicationLoadBalancers(String application) {
    def pattern = Keys.getLoadBalancerKey(googleCloudProvider, "*", "*", "${application}*")
    def identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, pattern)

    cacheView.getAll(LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.none()).collect {
      objectMapper.convertValue(it.attributes, GoogleLoadBalancer)
    }
  }

  ///
  /// Delete these - they're unused, therefore unnecessary.
  ///

  @Override
  Map<String, Set<GoogleLoadBalancer>> getLoadBalancers() {
    return null
  }

  @Override
  Set<GoogleLoadBalancer> getLoadBalancers(String account) {
    return null
  }

  @Override
  Set<GoogleLoadBalancer> getLoadBalancers(String account, String cluster) {
    return null
  }

  @Override
  Set<GoogleLoadBalancer> getLoadBalancers(String account, String cluster, String type) {
    return null
  }

  @Override
  Set<GoogleLoadBalancer> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    return null
  }

  @Override
  GoogleLoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    return null
  }
}
