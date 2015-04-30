/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.igor

import retrofit.http.GET
import retrofit.http.PUT
import retrofit.http.Path
import retrofit.http.QueryMap

interface IgorService {

  @PUT("/masters/{name}/jobs/{jobName}")
  Map<String, Object> build(@Path("name") String master, @Path("jobName") String jobName, @QueryMap Map<String,String> queryParams)

  @GET("/jobs/{master}/{job}/{buildNumber}")
  Map<String, Object> getBuild(@Path("master") String master,
                               @Path("job") String job,
                               @Path("buildNumber") Integer buildNumber)

  @GET("/jobs/{master}/{job}/{buildNumber}/properties/{fileName}")
  Map<String, Object> getPropertyFile(@Path("master") String master,
                                      @Path("job") String job,
                                      @Path("buildNumber") Integer buildNumber,
                                      @Path("fileName") String fileName)
}
