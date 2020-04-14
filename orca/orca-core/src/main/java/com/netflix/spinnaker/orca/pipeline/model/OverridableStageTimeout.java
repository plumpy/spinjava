/*
 * Copyright 2020 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.OptionalLong;
import javax.annotation.Nonnull;

public class OverridableStageTimeout {
  @Nonnull private final OptionalLong timeoutMinutes;

  public OverridableStageTimeout(@JsonProperty("timeoutMinutes") OptionalLong timeoutMinutes) {
    this.timeoutMinutes = timeoutMinutes;
  }

  @Nonnull
  public OptionalLong getTimeoutMinutes() {
    return timeoutMinutes;
  }
}
