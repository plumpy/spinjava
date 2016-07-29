/*
 * Copyright 2016 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.google.model.loadbalancing

class GoogleHttpLoadBalancingPolicy {
  /**
   * Describes the metric used to determine the serving capacity of the serverGroup.
   * Either UTILIZATION or RATE. maxRatePerInstance must be set if RATE, and
   * maxUtilization must be set if UTILIZATION.
   */
  BalancingMode balancingMode

  Float maxRatePerInstance

  Float maxUtilization

  static enum BalancingMode {
    UTILIZATION,
    RATE
  }
}
