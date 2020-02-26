/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.plugins.update.release

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import org.pf4j.update.PluginInfo

/**
 * Implement to select the desired release(s) from [PluginInfo].
 */
@Beta
interface PluginInfoReleaseProvider {

  /**
   * Get plugin releases from a list of plugin info objects
   */
  fun getReleases(pluginInfo: List<PluginInfo>): Set<PluginInfoRelease?>

  /**
   * Get a plugin release from a singular plugin info object
   */
  fun getRelease(pluginInfo: PluginInfo): PluginInfoRelease?
}

class PluginReleaseNotFoundException(pluginId: String, pluginVersion: String?) :
  IntegrationException(
    "'$pluginId' is enabled with version '${pluginVersion ?: "undefined" }', but a " +
      "release version could not be found that satisfies the version and/or the service " +
      "requirement constraints."
  )
