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
 *
 */

package com.netflix.spinnaker.front50.echo;

import java.util.Collection;
import java.util.Map;
import lombok.Data;
import lombok.NonNull;

@Data
public class Notification {
  @NonNull Type notificationType;
  @NonNull Collection<String> to;
  @NonNull String templateGroup;
  @NonNull Severity severity;
  @NonNull Source source;
  @NonNull Map<String, ?> additionalContext;

  @Data
  public static class Source {
    @NonNull String application;
  }

  public enum Type {
    EMAIL
  }

  public enum Severity {
    NORMAL,
    HIGH
  }
}
