/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.health

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import groovy.transform.InheritConstructors
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

import java.util.concurrent.atomic.AtomicReference

@Component
class GceHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(GceHealthIndicator)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)

  @Override
  Health health() {
    def ex = lastException.get()

    if (ex) {
      throw ex
    }

    new Health.Builder().up().build()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    try {
      Set<GoogleNamedAccountCredentials> googleCredentialsSet = accountCredentialsProvider.all.findAll {
        it instanceof GoogleNamedAccountCredentials
      } as Set<GoogleNamedAccountCredentials>

      if (!googleCredentialsSet) {
        throw new GoogleCredentialsNotFoundException()
      }

      for (GoogleNamedAccountCredentials accountCredentials in googleCredentialsSet) {
        try {
          GoogleCredentials googleCredentials = accountCredentials.credentials

          // This verifies that the specified credentials are sufficient to access the referenced project.
          googleCredentials.compute.projects().get(googleCredentials.project).execute()
        } catch (IOException e) {
          throw new GoogleIOException(e)
        }
      }

      lastException.set(null)
    } catch (Exception ex) {
      LOG.warn "Unhealthy", ex

      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = 'Google Module is configured, but no credentials found.')
  @InheritConstructors
  static class GoogleCredentialsNotFoundException extends RuntimeException {}

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with Google.")
  @InheritConstructors
  static class GoogleIOException extends RuntimeException {}
}
