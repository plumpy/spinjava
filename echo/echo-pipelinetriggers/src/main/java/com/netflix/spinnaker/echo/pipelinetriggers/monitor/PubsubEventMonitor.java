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

package com.netflix.spinnaker.echo.pipelinetriggers.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.trigger.PubsubEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Action1;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Triggers pipelines in _Orca_ when a trigger-enabled pubsub message arrives.
 */
@Component
@Slf4j
public class PubsubEventMonitor extends TriggerMonitor {

  public static final String PUBSUB_TRIGGER_TYPE = "pubsub";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PipelineCache pipelineCache;

  @Autowired
  public PubsubEventMonitor(@NonNull PipelineCache pipelineCache,
                            @NonNull Action1<Pipeline> subscriber,
                            @NonNull Registry registry) {
    super(subscriber, registry);
    this.pipelineCache = pipelineCache;
  }

  @Override
  public void processEvent(Event event) {
    super.validateEvent(event);
    if (!event.getDetails().getType().equalsIgnoreCase(PubsubEvent.TYPE)) {
      return;
    }

    PubsubEvent pubsubEvent = objectMapper.convertValue(event, PubsubEvent.class);

    Observable.just(pubsubEvent)
        .doOnNext(this::onEchoResponse)
        .subscribe(triggerEachMatchFrom(pipelineCache.getPipelines()));
  }

  @Override
  protected boolean isSuccessfulTriggerEvent(final TriggerEvent event) {
    PubsubEvent pubsubEvent = (PubsubEvent) event;
    return pubsubEvent != null;
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    PubsubEvent pubsubEvent = (PubsubEvent) event;
    MessageDescription description = pubsubEvent.getContent().getMessageDescription();
    return trigger -> pipeline
        .withReceivedArtifacts(description.getArtifacts())
        .withTrigger(trigger.atMessageDescription(description.getSubscriptionName(), description.getPubsubSystem().toString()));
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    return trigger.isEnabled()
        && isPubsubTrigger(trigger);
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event) {
    PubsubEvent pubsubEvent = (PubsubEvent) event;
    MessageDescription description = pubsubEvent.getContent().getMessageDescription();

    return trigger -> trigger.getType().equalsIgnoreCase(PUBSUB_TRIGGER_TYPE)
        && trigger.getPubsubSystem().equalsIgnoreCase(description.getPubsubSystem().toString())
        && trigger.getSubscriptionName().equalsIgnoreCase(description.getSubscriptionName())
        && anyArtifactsMatchExpected(description.getArtifacts(), trigger);
  }

  private Boolean anyArtifactsMatchExpected(List<Artifact> messageArtifacts, Trigger trigger) {
    List<ExpectedArtifact> expectedArtifacts = trigger.getExpectedArtifacts();

    if (expectedArtifacts == null || expectedArtifacts.isEmpty()) {
      return true;
    }

    if (messageArtifacts.size() > expectedArtifacts.size()) {
      log.warn("Parsed message artifacts (size {}) greater than expected artifacts (size {}), continuing trigger anyway", messageArtifacts.size(), expectedArtifacts.size());
    }

    Predicate<Artifact> expectedArtifactMatch = a -> trigger.getExpectedArtifacts()
        .stream()
        .anyMatch(e -> expectedMatch(e, a));
    return messageArtifacts.stream().anyMatch(expectedArtifactMatch);
  }

  private Boolean expectedMatch(ExpectedArtifact e, Artifact a) {
    return e.getFields().stream()
        .filter(field -> field.getFieldType().equals(ExpectedArtifact.ArtifactField.FieldType.MUST_MATCH))
        .allMatch(field -> { // Look up the field in the actual artifact and check that the values match.
          try {
            Field declaredField = a.getClass().getDeclaredField(field.getFieldName());
            declaredField.setAccessible(true);
            String actualValue = (String) declaredField.get(a); // Note: all fields we can match on are Strings.
            declaredField.setAccessible(false);
            return actualValue.equals(field.getValue());
          } catch (IllegalAccessException | NoSuchFieldException ex) {
            log.error(ex.getMessage());
            return false;
          }
        });
  }

  @Override
  protected void onMatchingPipeline(Pipeline pipeline) {
    super.onMatchingPipeline(pipeline);
    val id = registry.createId("pipelines.triggered")
        .withTag("application", pipeline.getApplication())
        .withTag("name", pipeline.getName());

    if (isPubsubTrigger(pipeline.getTrigger())) {
      id.withTag("pubsubSystem", pipeline.getTrigger().getPubsubSystem());
      id.withTag("subscriptionName", pipeline.getTrigger().getSubscriptionName());
    }

    registry.counter(id).increment();
  }

  private boolean isPubsubTrigger(Trigger trigger) {
    return PUBSUB_TRIGGER_TYPE.equals(trigger.getType())
        && !StringUtils.isEmpty(trigger.getSubscriptionName())
        && !StringUtils.isEmpty(trigger.getPubsubSystem());
  }
}
