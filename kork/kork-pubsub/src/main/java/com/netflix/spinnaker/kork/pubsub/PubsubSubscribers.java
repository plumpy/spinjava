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

package com.netflix.spinnaker.kork.pubsub;

import com.netflix.spinnaker.kork.pubsub.model.PubsubSubscriber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PubsubSubscribers {
  private List<PubsubSubscriber> subscribers = new ArrayList<>();

  public void putAll(List<PubsubSubscriber> newEntries) {
    subscribers.addAll(newEntries);
  }

  public List<PubsubSubscriber> getAll() {
    return Collections.unmodifiableList(subscribers);
  }

  public List<PubsubSubscriber> withType(String pubsubSystem) {
    return subscribers.stream()
        .filter(subscriber -> subscriber.getPubsubSystem().equals(pubsubSystem))
        .collect(Collectors.toList());
  }
}
