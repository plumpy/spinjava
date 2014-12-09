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

package com.netflix.spinnaker.kato.gce.deploy.description

import com.netflix.spinnaker.kato.gce.security.GoogleCredentials

/**
 * Description for "enabling" a supplied Google Server Group. "Enabling" means setting a network load balancer's target
 * pool on the server group.
 *
 * Description for "disabling" a supplied Google Server Group. "Disabling" means removing all target pool associations
 * from the server group.
 */
class EnableDisableGoogleServerGroupDescription {
  String replicaPoolName
  String zone
  String accountName
  GoogleCredentials credentials
}
