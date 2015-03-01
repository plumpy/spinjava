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

package com.netflix.spinnaker.rosco.providers

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest

interface CloudProviderBakeHandler {

  String produceBakeKey(String region, BakeRequest bakeRequest)

  /**
   * Build provider-specific script command for packer.
   */
  String producePackerCommand(String region, BakeRequest bakeRequest)

  /**
   * Returns true if this cloud provider is the producer of this first line of logs content.
   */
  boolean isProducerOf(String logsContentFirstLine)

  /**
   * Returns the details of a completed bake.
   * TODO(duftler): This is temporary. Remove the scraping logic when a better solution for
   * determining image id/name is in place.
   */
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent)

}
