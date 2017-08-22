/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.prometheus.security;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Builder
@Data
@Slf4j
// TODO(ewisbelatt): Not sure what kind of credentials or configuration is really required here yet.
//       Prometheus does not have security. To secure it, you front it with a webserver that adds security (e.g. basic auth).
//       So this is likely just a username/password.
public class PrometheusCredentials {

  private static String applicationVersion =
    Optional.ofNullable(PrometheusCredentials.class.getPackage().getImplementationVersion()).orElse("Unknown");
}
