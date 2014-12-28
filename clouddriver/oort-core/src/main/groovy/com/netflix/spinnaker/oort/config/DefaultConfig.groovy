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

package com.netflix.spinnaker.oort.config

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.amos.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.oort.model.*
import com.netflix.spinnaker.oort.search.NoopSearchProvider
import com.netflix.spinnaker.oort.search.SearchProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import java.util.concurrent.TimeUnit

@Configuration
class DefaultConfig {

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsRepository)
  AccountCredentialsRepository accountCredentialsRepository() {
    new MapBackedAccountCredentialsRepository()
  }

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsProvider)
  AccountCredentialsProvider accountCredentialsProvider(AccountCredentialsRepository accountCredentialsRepository) {
    new DefaultAccountCredentialsProvider(accountCredentialsRepository)
  }

  @Bean
  @ConditionalOnMissingBean(NamedCacheFactory)
  NamedCacheFactory namedCacheFactory() {
    new InMemoryNamedCacheFactory()
  }

  @Bean
  @ConditionalOnMissingBean(AgentScheduler)
  AgentScheduler agentScheduler() {
    new DefaultAgentScheduler(60, TimeUnit.SECONDS)
  }

  @Bean
  @ConditionalOnMissingBean(Provider)
  Provider noopProvider() {
    new Provider() {
      @Override
      String getProviderName() {
        "noop"
      }

      @Override
      Collection<CachingAgent> getCachingAgents() {
        Collections.emptySet()
      }
    }
  }

  @Bean
  @ConditionalOnMissingBean(SearchProvider)
  SearchProvider noopSearchProvider() {
    new NoopSearchProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ApplicationProvider)
  ApplicationProvider noopApplicationProvider() {
    new NoopApplicationProvider()
  }

  @Bean
  @ConditionalOnMissingBean(LoadBalancerProvider)
  LoadBalancerProvider noopLoadBalancerProvider() {
    new NoopLoadBalancerProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ClusterProvider)
  ClusterProvider noopClusterProvider() {
    new NoopClusterProvider()
  }

  @Bean
  @ConditionalOnMissingBean(OnDemandCacheUpdater)
  OnDemandCacheUpdater noopOnDemandCacheUpdater() {
    new NoopOnDemandCacheUpdater()
  }

  @Bean
  @ConditionalOnMissingBean(ExecutionInstrumentation)
  ExecutionInstrumentation noopExecutionInstrumentation() {
    new NoopExecutionInstrumentation()
  }

  @Bean
  @ConditionalOnMissingBean(InstanceProvider)
  InstanceProvider noopInstanceProvider() {
    new NoopInstanceProvider()
  }

  @Bean
  @ConditionalOnMissingBean(AgentScheduler)
  AgentScheduler defaultAgentScheduler(@Value('${pollIntervalSeconds:60') long pollIntervalSeconds) {
    new DefaultAgentScheduler(pollIntervalSeconds, TimeUnit.SECONDS)
  }

  @Bean
  @ConditionalOnMissingBean(CatsModule)
  CatsModule catsModule(List<Provider> providers, List<ExecutionInstrumentation> executionInstrumentation, NamedCacheFactory cacheFactory, AgentScheduler agentScheduler) {
    new CatsModule.Builder().cacheFactory(cacheFactory).scheduler(agentScheduler).instrumentation(executionInstrumentation).build(providers)
  }

  @Bean
  Cache cacheView(CatsModule catsModule) {
    catsModule.view
  }
}
