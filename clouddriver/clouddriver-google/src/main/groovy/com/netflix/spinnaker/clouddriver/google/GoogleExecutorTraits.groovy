/*
 * Copyright 2017 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.google

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpResponseException
import com.netflix.spinnaker.clouddriver.googlecommon.GoogleExecutor

import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry

import java.util.concurrent.TimeUnit


/**
 * This class is syntactic sugar atop the static GoogleExecutor.
 * By making it a traite, we can wrap the calls with less in-line syntax.
 */
trait GoogleExecutorTraits {
  final String TAG_BATCH_CONTEXT = GoogleExecutor.TAG_BATCH_CONTEXT
  final String TAG_REGION = GoogleExecutor.TAG_REGION
  final String TAG_SCOPE = GoogleExecutor.TAG_SCOPE
  final String TAG_ZONE = GoogleExecutor.TAG_ZONE
  final String SCOPE_BATCH = GoogleExecutor.SCOPE_BATCH
  final String SCOPE_GLOBAL = GoogleExecutor.SCOPE_GLOBAL
  final String SCOPE_REGIONAL = GoogleExecutor.SCOPE_REGIONAL
  final String SCOPE_ZONAL = GoogleExecutor.SCOPE_ZONAL

  abstract Registry getRegistry()

  public <T> T timeExecuteBatch(BatchRequest batch, String batchContext, String... tags) throws IOException {
     return GoogleExecutor.timeExecuteBatch(getRegistry(), batch, batchContext, tags)
  }

  public <T> T timeExecute(AbstractGoogleClientRequest<T> request, String api, String... tags) throws IOException {
     return GoogleExecutor.timeExecute(getRegistry(), request, "google.api", api, tags)
  }
}

