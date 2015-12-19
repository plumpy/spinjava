/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.controllers.CacheController
import com.netflix.spinnaker.clouddriver.controllers.ConfigRefreshController
import com.netflix.spinnaker.clouddriver.controllers.CredentialsController
import com.netflix.spinnaker.clouddriver.controllers.ElasticIpController
import com.netflix.spinnaker.clouddriver.controllers.InstanceTypeController
import com.netflix.spinnaker.clouddriver.controllers.KeyPairController
import com.netflix.spinnaker.clouddriver.controllers.NetworkController
import com.netflix.spinnaker.clouddriver.controllers.OperationsController
import com.netflix.spinnaker.clouddriver.controllers.SearchController
import com.netflix.spinnaker.clouddriver.controllers.SecurityGroupController
import com.netflix.spinnaker.clouddriver.controllers.SubnetController
import com.netflix.spinnaker.clouddriver.controllers.TaskController
import com.netflix.spinnaker.clouddriver.controllers.VpcController
import com.netflix.spinnaker.clouddriver.filters.SimpleCORSFilter
import com.netflix.spinnaker.clouddriver.listeners.CredentialsRefreshListener
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.filter.ShallowEtagHeaderFilter
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

import javax.servlet.Filter

@Configuration
@Import([
  SimpleCORSFilter,
  CacheController,
  ConfigRefreshController,
  CredentialsController,
  CredentialsRefreshListener,
  ElasticIpController,
  InstanceTypeController,
  KeyPairController,
  NetworkController,
  OperationsController,
  SearchController,
  SecurityGroupController,
  SubnetController,
  TaskController,
  VpcController
])
public class WebConfig extends WebMvcConfigurerAdapter {
  @Autowired
  Registry registry

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
      new MetricsInterceptor(
        this.registry, "controller.invocations", ["account", "region"], ["BasicErrorController"]
      )
    )
  }

  @Bean
  Filter eTagFilter() {
    new ShallowEtagHeaderFilter()
  }

  @Bean
  Filter authenticatedRequestFilter() {
    new AuthenticatedRequestFilter(true)
  }

  @Bean
  Filter corsFilter() {
    new SimpleCORSFilter()
  }

  @Override
  void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    super.configureContentNegotiation(configurer)
    configurer.favorPathExtension(false);
  }

}
