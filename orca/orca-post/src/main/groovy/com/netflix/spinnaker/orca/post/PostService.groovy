/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.post

import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.Headers
import retrofit.http.POST

interface PostService {
  @Headers("Content-type: application/json")
  @POST("/notifications")
  Response create(@Body Notification notification)

  static class Notification {
    Type notificationType
    Collection<String> to
    String templateGroup
    Severity severity

    Source source
    static class Source {
      String executionType
      String executionId
      String application
    }

    static enum Type {
      HIPCHAT,
      EMAIL,
      SMS
    }

    static enum Severity {
      NORMAL,
      HIGH
    }
  }
}
