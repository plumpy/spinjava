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

package com.netflix.spinnaker.gate.services.internal

import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.DELETE
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path

interface MayoService {

  @GET('/pipelines/{app}')
  List<Map> getPipelineConfigs(@Path("app") String app)

  @GET('/pipelines/{app}/{name}')
  Map getPipelineConfig(@Path("app") String app, @Path("name") String name)

  @DELETE('/pipelines/{app}/{name}')
  Response deletePipelineConfig(@Path("app") String app, @Path("name") String name)

  @POST('/pipelines')
  Response savePipelineConfig(@Body Map pipelineConfig)

  @POST('/pipelines/move')
  Response move(@Body Map moveCommand)

  @GET('/notifications/{type}/{app}')
  Map getNotificationConfigs(@Path('type') String type, @Path('app') String app)

  @DELETE('/notifications/{type}/{app}')
  Response deleteNotificationConfig(@Path('type') String type, @Path('app') String app)

  @POST('/notifications/{type}/{app}')
  Response saveNotificationConfig(@Path('type') String type, @Path('app') String app, @Body Map notificationConfig)
}
