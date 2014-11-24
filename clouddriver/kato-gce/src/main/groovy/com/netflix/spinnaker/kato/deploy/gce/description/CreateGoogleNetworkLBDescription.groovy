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

package com.netflix.spinnaker.kato.deploy.gce.description

import com.netflix.spinnaker.kato.security.gce.GoogleCredentials

class CreateGoogleNetworkLBDescription {
  String networkLBName
  HealthCheck healthCheck
  // The URLs of the instances.
  List<String> instances
  String ipAddress
  String portRange
  String zone
  String accountName
  GoogleCredentials credentials

  static class HealthCheck {
    Integer checkIntervalSec
    Integer healthyThreshold
    Integer unhealthyThreshold
    Integer port
    Integer timeoutSec
    String requestPath
  }
}
