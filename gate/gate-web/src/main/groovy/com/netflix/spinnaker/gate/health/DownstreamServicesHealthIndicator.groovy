/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.gate.health

import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.netflix.spinnaker.gate.config.GateConfig
import com.netflix.spinnaker.gate.config.Service
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.services.internal.HealthCheckableService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.OkClient

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import static retrofit.Endpoints.newFixedEndpoint

@Component
class DownstreamServicesHealthIndicator extends AbstractHealthIndicator {
  final Map<String, HealthCheckableService> healthCheckableServices

  AtomicReference<Map<String, String>> failuresByService = new AtomicReference<>([:])
  AtomicBoolean skipDownstreamServiceChecks = new AtomicBoolean(false)

  @Autowired
  DownstreamServicesHealthIndicator(OkHttpClientConfiguration okHttpClientConfiguration,
                                    ServiceConfiguration serviceConfiguration) {
    this(
      serviceConfiguration.services.findResults { String name, Service service ->
        if (!service.enabled || !serviceConfiguration.healthCheckableServices.contains(name)) {
          return null
        }

        return [
          name, new RestAdapter.Builder()
          .setEndpoint(newFixedEndpoint(service.baseUrl))
          .setClient(new OkClient(okHttpClientConfiguration.create()))
          .build()
          .create(HealthCheckableService)
        ]
      }.collectEntries()
    )
  }

  DownstreamServicesHealthIndicator(Map<String, HealthCheckableService> healthCheckableServices) {
    this.healthCheckableServices = healthCheckableServices
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) throws Exception {
    def attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes()
    if (!attributes) {
      return
    }

    def httpServletRequest = attributes.getRequest()
    if (Boolean.valueOf(httpServletRequest.getParameter("checkDownstreamServices") ?: "false")) {
      // force a re-check of downstream services
      skipDownstreamServiceChecks.set(false)
    }

    if (!Boolean.valueOf(httpServletRequest.getParameter("downstreamServices") ?: "false")) {
      // do not consider downstream service health (will result in UNKNOWN)
      return
    }

    failuresByService.get().isEmpty() ? builder.up() : builder.down().withDetail("errors", failuresByService.get())
  }

  @Scheduled(fixedDelay = 30000L)
  void checkDownstreamServiceHealth() {
    if (skipDownstreamServiceChecks.get()) {
      return
    }

    def serviceHealths = [:]
    healthCheckableServices.each { String name, HealthCheckableService service ->
      try {
        service.health()
      } catch (RetrofitError e) {
        serviceHealths[name] = "${e.message} (url: ${e.url})".toString()
      }
    }

    if (!serviceHealths) {
      // do not continue checking downstream services once successful
      skipDownstreamServiceChecks.set(true)
    }

    failuresByService.set(serviceHealths)
  }

}
