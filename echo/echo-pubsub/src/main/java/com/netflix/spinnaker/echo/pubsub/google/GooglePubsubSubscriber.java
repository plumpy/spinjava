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

package com.netflix.spinnaker.echo.pubsub.google;

import com.google.api.core.ApiService;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.SubscriptionName;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.model.MessageInstanceDescription;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.model.PubsubType;
import com.netflix.spinnaker.echo.pubsub.utils.NodeIdentity;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.threeten.bp.Duration;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GooglePubsubSubscriber implements PubsubSubscriber {

  private String name;

  private String project;

  private String topic;

  @Getter
  private Subscriber subscriber;

  private Integer ackDeadlineSeconds;

  static private final PubsubType pubsubType = PubsubType.GOOGLE;


  public GooglePubsubSubscriber(String name, String project, Subscriber subscriber, Integer ackDeadlineSeconds) {
    this.name = name;
    this.project = project;
    this.topic = String.format("projects/%s/topics/%s", project, name);
    this.subscriber = subscriber;
    this.ackDeadlineSeconds = ackDeadlineSeconds;
  }

  @Override
  public PubsubType pubsubType() {
    return pubsubType;
  }

  @Override
  public String topic() {
    return topic;
  }

  public static GooglePubsubSubscriber buildSubscriber(String name,
                                                       String project,
                                                       String jsonPath,
                                                       Integer ackDeadlineSeconds,
                                                       PubsubMessageHandler pubsubMessageHandler) {
    Subscriber subscriber;
    GooglePubsubMessageReceiver messageReceiver = new GooglePubsubMessageReceiver(ackDeadlineSeconds, name, pubsubMessageHandler);

    if (jsonPath != null && !jsonPath.isEmpty()) {
      Credentials credentials = null;
      try {
        credentials = ServiceAccountCredentials.fromStream(new FileInputStream(jsonPath));
      } catch (IOException e) {
        log.error("Could not import Google Pubsub json credentials: {}", e.getMessage());
      }
      subscriber = Subscriber
          .defaultBuilder(SubscriptionName.create(project, name), messageReceiver)
          .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
          .setMaxAckExtensionPeriod(Duration.ofSeconds(0))
          .build();
    } else {
      subscriber = Subscriber.defaultBuilder(SubscriptionName.create(project, name), messageReceiver).build();
    }

    subscriber.addListener(new GooglePubsubFailureHandler(), MoreExecutors.directExecutor());

    return new GooglePubsubSubscriber(name, project, subscriber, ackDeadlineSeconds);
  }

  private static class GooglePubsubMessageReceiver implements MessageReceiver {

    private Integer ackDeadlineSeconds;

    private PubsubMessageHandler pubsubMessageHandler;

    private String subscriptionName;

    private NodeIdentity identity = new NodeIdentity();

    public GooglePubsubMessageReceiver(Integer ackDeadlineSeconds,
                                       String subscriptionName,
                                       PubsubMessageHandler pubsubMessageHandler) {
      this.ackDeadlineSeconds = ackDeadlineSeconds;
      this.subscriptionName = subscriptionName;
      this.pubsubMessageHandler = pubsubMessageHandler;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      String messagePayload = message.getData().toStringUtf8();
      log.debug("Received message with payload: {}", messagePayload);

      MessageInstanceDescription description = MessageInstanceDescription.builder()
          .subscriptionName(subscriptionName)
          .messagePayload(messagePayload)
          .pubsubType(pubsubType)
          .ackDeadlineMillis(5 * TimeUnit.SECONDS.toMillis(ackDeadlineSeconds)) // Set a high upper bound on message processing time.
          .retentionDeadlineMillis(TimeUnit.DAYS.toMillis(7)) // Expire key after max retention time, which is 7 days.
          .build();
      GoogleMessageAcknowledger acknowledger = new GoogleMessageAcknowledger(consumer);
      pubsubMessageHandler.handleMessage(description, acknowledger, identity.getIdentity());
    }
  }

  private static class GooglePubsubFailureHandler extends ApiService.Listener {
    @Override
    public void failed(ApiService.State from, Throwable failure) {
      log.error("Google Pubsub listener failure caused by {}", failure.getMessage());
    }
  }
}
