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

package com.netflix.spinnaker.oort.gce.model.callbacks

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.InstanceTemplate
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import org.apache.log4j.Logger

class InstanceTemplatesCallback<InstanceTemplate> extends JsonBatchCallback<InstanceTemplate> {
  protected static final Logger log = Logger.getLogger(this)

  private GoogleServerGroup googleServerGroup

  public InstanceTemplatesCallback(GoogleServerGroup googleServerGroup) {
    this.googleServerGroup = googleServerGroup
  }

  @Override
  void onSuccess(InstanceTemplate instanceTemplate, HttpHeaders responseHeaders) throws IOException {
    googleServerGroup.launchConfig.launchConfigurationName = instanceTemplate?.name
    googleServerGroup.launchConfig.instanceType = instanceTemplate?.properties?.machineType

    def sourceImage = instanceTemplate?.properties?.disks?.find { disk ->
      disk.boot
    }?.initializeParams?.sourceImage

    if (sourceImage) {
      googleServerGroup.launchConfig.imageId = Utils.getLocalName(sourceImage)
    }
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}
