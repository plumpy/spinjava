/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.atlas.service;

import com.netflix.kayenta.atlas.model.AtlasResults;
import retrofit.http.GET;
import retrofit.http.Query;

import java.time.Instant;
import java.util.List;

public interface AtlasRemoteService {

  @GET("/api/v2/fetch")
  List<AtlasResults> fetch(@Query("q") String q,
                           @Query("s") Instant start,
                           @Query("e") Instant end,
                           @Query("step") String step);
}
