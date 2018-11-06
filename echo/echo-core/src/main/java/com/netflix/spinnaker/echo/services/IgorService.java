/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.services;

import retrofit.http.GET;
import retrofit.http.Path;

import java.util.Map;

public interface IgorService {
  @GET("/builds/status/{buildNumber}/{master}/{job}")
  Map<String, Object> getBuild(@Path("buildNumber") Integer buildNumber,
    @Path("master") String master,
    @Path("job") String job);

  @GET("/builds/properties/{buildNumber}/{fileName}/{master}/{job}")
  Map<String, Object> getPropertyFile(@Path("buildNumber") Integer buildNumber,
    @Path("fileName") String fileName,
    @Path("master") String master,
    @Path("job") String job);
}
