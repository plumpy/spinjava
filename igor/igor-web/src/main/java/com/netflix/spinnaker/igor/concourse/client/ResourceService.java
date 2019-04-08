/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.concourse.client;

import com.netflix.spinnaker.igor.concourse.client.model.Resource;
import retrofit.http.GET;
import retrofit.http.Path;

import java.util.Collection;

public interface ResourceService {
  @GET("/api/v1/teams/{team}/pipelines/{pipeline}/resources")
  Collection<Resource> resources(@Path("team") String team, @Path("pipeline") String pipeline);
}
