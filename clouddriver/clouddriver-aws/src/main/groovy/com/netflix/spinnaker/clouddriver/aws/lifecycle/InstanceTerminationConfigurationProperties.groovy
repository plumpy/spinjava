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

package com.netflix.spinnaker.clouddriver.aws.lifecycle

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aws.lifecycleSubscribers.instanceTermination")
class InstanceTerminationConfigurationProperties {
  String accountName
  String queueARN
  String topicARN

  int maxMessagesPerCycle = 1000
  int visibilityTimeout = 30
  int waitTimeSeconds = 5
  int pollIntervalSeconds = 30

  InstanceTerminationConfigurationProperties() {
    // default constructor
  }

  InstanceTerminationConfigurationProperties(String accountName,
                                             String queueARN,
                                             String topicARN,
                                             int maxMessagesPerCycle,
                                             int visibilityTimeout,
                                             int waitTimeSeconds,
                                             int pollIntervalSeconds) {
    this.accountName = accountName
    this.queueARN = queueARN
    this.topicARN = topicARN
    this.maxMessagesPerCycle = maxMessagesPerCycle
    this.visibilityTimeout = visibilityTimeout
    this.waitTimeSeconds = waitTimeSeconds
    this.pollIntervalSeconds = pollIntervalSeconds
  }
}
