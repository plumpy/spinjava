/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.model

/**
 * Primarily a marker interface, but provides the representation of an instance, which exists within a {@link ServerGroup}. Concrete implementations should provide more-specific data.
 *
 * @author Dan Woods
 */
interface Instance {
  /**
   * The name of the instance
   *
   * @return instance name
   */
  String getName()

  /**
   * A flag indicating whether the instance is healthy
   *
   * @return true/false depending on healthy/unhealthy
   */
  boolean isHealthy()

  /**
   * A timestamp indicating when the instance was launched
   *
   * @return the number of milliseconds after the beginning of time (1 January, 1970 UTC) when
   * this instance was launched
   */
  Long getLaunchTime()

  /**
   * A list of all health metrics reported for this instance. This will always
   * include keys for type and status, and may include others, depending on the
   * health metric
   */
  List<Map<String, String>> getHealth()
}