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
package com.netflix.spinnaker.orca.api.pipeline.models;

import com.netflix.spinnaker.kork.annotations.Beta;
import javax.annotation.Nonnull;

/** A trigger type that carries source code-related metadata. */
@Beta
public interface SourceCodeTrigger extends Trigger {

  @Nonnull
  String getSource();

  @Nonnull
  String getProject();

  @Nonnull
  String getBranch();

  @Nonnull
  String getSlug();

  @Nonnull
  String getHash();
}
