/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.ivy;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("artifacts.ivy.enabled")
@EnableConfigurationProperties(IvyArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
class IvyArtifactConfiguration {
  private final IvyArtifactProviderProperties ivyArtifactProviderProperties;

  @Bean
  List<? extends IvyArtifactCredentials> ivyArtifactCredentials() {
    return ivyArtifactProviderProperties.getAccounts().stream()
        .map(
            a -> {
              try {
                return new IvyArtifactCredentials(a);
              } catch (Exception e) {
                log.warn("Failure instantiating ivy artifact account {}: ", a, e);
                return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
