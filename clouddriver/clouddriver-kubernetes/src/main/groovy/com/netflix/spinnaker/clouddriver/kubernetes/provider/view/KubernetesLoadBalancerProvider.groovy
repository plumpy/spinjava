/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesLoadBalancer
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesServerGroup
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.Service
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesLoadBalancerProvider implements LoadBalancerProvider<KubernetesLoadBalancer> {
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesLoadBalancerProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<KubernetesLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    String loadBalancerGlobKey = Keys.getLoadBalancerKey("*", "*", KubernetesUtil.combineAppStackDetail(applicationName, '*', null))
    String applicationKey = Keys.getApplicationKey(applicationName)

    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, applicationKey)

    Set<CacheData> loadBalancers = KubernetesProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.LOAD_BALANCERS.ns, loadBalancerGlobKey)
    loadBalancers.addAll(KubernetesProviderUtils.resolveRelationshipData(cacheView, application, Keys.Namespace.LOAD_BALANCERS.ns))
    Set<CacheData> allServerGroups = KubernetesProviderUtils.resolveRelationshipDataForCollection(cacheView, loadBalancers, Keys.Namespace.SERVER_GROUPS.ns)

    Map<String, KubernetesServerGroup> serverGroupMap = allServerGroups.collectEntries { serverGroupData ->
      ReplicationController replicationController = objectMapper.convertValue(serverGroupData.attributes.replicationController, ReplicationController)
      [(serverGroupData.id): new KubernetesServerGroup(replicationController, [])] // Not collecting pods as they manage their own health checks.
    }

    return loadBalancers.collect {
      translateLoadBalancer(it, serverGroupMap)
    } as Set
  }

  private KubernetesLoadBalancer translateLoadBalancer(CacheData loadBalancerEntry, Map<String, KubernetesServerGroup> serverGroupMap) {
    def parts = Keys.parse(loadBalancerEntry.id)
    Service service = objectMapper.convertValue(loadBalancerEntry.attributes.service, Service)
    List<KubernetesServerGroup> serverGroups = []
    loadBalancerEntry.relationships[Keys.Namespace.SERVER_GROUPS.ns]?.forEach {
      serverGroups << serverGroupMap[it]
    }

    return new KubernetesLoadBalancer(service, serverGroups, parts.account)
  }
}
