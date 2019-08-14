/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus.deploy.events;

import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import javax.annotation.Nonnull;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class TitusScalingPolicyCopied extends SagaEvent {
  @Nonnull private final String serverGroupName;
  @Nonnull private final String region;
  @Nonnull private final String sourcePolicyId;

  public TitusScalingPolicyCopied(
      @NotNull String sagaName,
      @NotNull String sagaId,
      @Nonnull String serverGroupName,
      @Nonnull String region,
      @Nonnull String sourcePolicyId) {
    super(sagaName, sagaId);
    this.serverGroupName = serverGroupName;
    this.region = region;
    this.sourcePolicyId = sourcePolicyId;
  }
}
