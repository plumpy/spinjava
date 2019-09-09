/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Autoscaler;
import com.google.api.services.compute.model.RegionAutoscalerList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;

public final class RegionAutoscalers {

  private final Compute.RegionAutoscalers computeApi;
  private final GoogleNamedAccountCredentials credentials;
  private final RegionalGoogleComputeRequestFactory requestFactory;

  RegionAutoscalers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry) {
    this.computeApi = credentials.getCompute().regionAutoscalers();
    this.credentials = credentials;
    this.requestFactory =
        new RegionalGoogleComputeRequestFactory(
            "regionAutoscalers", credentials, operationPoller, registry);
  }

  public GoogleComputeGetRequest<Compute.RegionAutoscalers.Get, Autoscaler> get(
      String region, String name) throws IOException {
    return requestFactory.wrapGetRequest(
        computeApi.get(credentials.getProject(), region, name), "get", region);
  }

  public PaginatedComputeRequest<Compute.RegionAutoscalers.List, Autoscaler> list(String region) {
    return new PaginatedComputeRequestImpl<>(
        pageToken ->
            requestFactory.wrapRequest(
                computeApi.list(credentials.getProject(), region).setPageToken(pageToken),
                "list",
                region),
        RegionAutoscalerList::getNextPageToken,
        RegionAutoscalerList::getItems);
  }
}
