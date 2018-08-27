/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.echo.model.pubsub;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDescription {
  /**
   * Logical name given to the subscription by the user, not the locator
   * the pub/sub system uses.
   */
  private String subscriptionName;

  private String messagePayload;

  /**
   * Optional, additional message metadata sent from the pub/sub system.
   *
   * May be null.
   */
  private Map<String, String> messageAttributes;

  private PubsubSystem pubsubSystem;

  private Integer ackDeadlineSeconds;

  private Integer retentionDeadlineSeconds;

  /**
   * List of artifacts parsed from the pub/sub message.
   */
  private List<Artifact> artifacts;
}
