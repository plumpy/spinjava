/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class S3Event {
  @JsonProperty("Records")
  public List<S3EventRecord> records;

  public static class S3EventRecord {
    public String eventName;
    public String eventTime;
    public S3Meta s3;
  }

  public static class S3Meta {
    public S3Object object;
  }

  public static class S3Object {
    public String key;
  }
}
