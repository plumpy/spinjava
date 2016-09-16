/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.network.model

import com.netflix.spinnaker.clouddriver.azure.resources.subnet.model.AzureSubnet
import com.netflix.spinnaker.clouddriver.model.Network

class AzureNetwork implements Network {
  String cloudProvider
  String id
  String name
  String account
  String region
  String resourceGroup
  List<AzureSubnet> subnets = []
}
