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

package com.netflix.spinnaker.clouddriver.google.security

import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GoogleCredentialsInitializer {
  private static final Logger log = Logger.getLogger(this.class.simpleName)

  @Autowired
  String applicationName

  @Bean
  List<? extends GoogleNamedAccountCredentials> googleNamedAccountCredentials(
    GoogleConfigurationProperties googleConfigurationProperties,
    AccountCredentialsRepository accountCredentialsRepository) {
    List<? extends GoogleNamedAccountCredentials> googleAccounts = []

    for (managedAccount in googleConfigurationProperties.accounts) {
      try {
        // TODO(duftler): Pass applicationName to GoogleNamedAccountCredentials constructor.
        def googleAccount = new GoogleNamedAccountCredentials(googleConfigurationProperties.kmsServer, managedAccount.name, managedAccount.project)

        googleAccounts << accountCredentialsRepository.save(managedAccount.name, googleAccount)
      } catch (e) {
        log.info "Could not load account ${managedAccount.name} for Google.", e
      }
    }

    googleAccounts
  }
}
