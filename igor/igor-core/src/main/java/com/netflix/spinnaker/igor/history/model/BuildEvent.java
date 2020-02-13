/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.igor.history.model;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

public abstract class BuildEvent<T extends BuildContent> implements Event {

  private static final Map<String, String> BUILD_DETAILS =
      ImmutableMap.<String, String>builder().put("type", "build").put("source", "igor").build();

  public abstract T getContent();

  public final Map<String, String> getDetails() {
    return BUILD_DETAILS;
  }
}
