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

package com.netflix.spinnaker.oort.aws.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.oort.aws.discovery.DiscoveryApi
import com.netflix.spinnaker.oort.aws.data.Keys
import com.netflix.spinnaker.oort.aws.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.oort.aws.model.discovery.DiscoveryApplications
import com.netflix.spinnaker.oort.aws.model.discovery.DiscoveryInstance
import com.netflix.spinnaker.oort.aws.provider.AwsProvider

import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.INSTANCES

class DiscoveryCachingAgent implements HealthProvidingCachingAgent {
  private final List<NetflixAmazonCredentials> accounts
  private final String region
  private final DiscoveryApi discoveryApi
  private final ObjectMapper objectMapper
  private final String discoveryHost
  final String healthId = "discovery"

  DiscoveryCachingAgent(DiscoveryApi discoveryApi, List<NetflixAmazonCredentials> accounts, String region, ObjectMapper objectMapper) {
    this.accounts = accounts
    this.region = region
    this.discoveryApi = discoveryApi
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.discoveryHost = String.format(accounts[0].discovery, region)
  }

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${discoveryHost}/${DiscoveryCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {

    DiscoveryApplications disco = discoveryApi.loadDiscoveryApplications()

    Collection<CacheData> discoveryCacheData = new LinkedList<CacheData>()
    Collection<CacheData> instanceCacheData = new LinkedList<CacheData>()

    for (DiscoveryApplication application : disco.applications) {
      for (DiscoveryInstance instance : application.instances) {
        if (instance.instanceId) {
          for (NetflixAmazonCredentials account : accounts) {
            String instanceKey = Keys.getInstanceKey(instance.instanceId, account.name, region)
            String instanceHealthKey = Keys.getInstanceHealthKey(instance.instanceId, account.name, region, healthId)
            Map<String, Object> attributes = objectMapper.convertValue(instance, ATTRIBUTES)
            Map<String, Collection<String>> relationships = [(INSTANCES.ns):[instanceKey]]
            discoveryCacheData.add(new DefaultCacheData(instanceHealthKey, attributes, relationships))
            instanceCacheData.add(new DefaultCacheData(instanceKey, [:], [(HEALTH.ns):[instanceHealthKey]]))
          }
        }
      }
    }

    new DefaultCacheResult(
      (INSTANCES.ns): instanceCacheData,
      (HEALTH.ns): discoveryCacheData)
  }
}
