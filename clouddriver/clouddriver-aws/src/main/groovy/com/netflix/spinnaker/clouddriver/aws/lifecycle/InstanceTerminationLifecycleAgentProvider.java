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
package com.netflix.spinnaker.clouddriver.aws.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentProvider;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;

import javax.inject.Provider;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InstanceTerminationLifecycleAgentProvider implements AgentProvider {
  private final static String REGION_TEMPLATE_PATTERN = Pattern.quote("{{region}}");
  private final static String ACCOUNT_ID_TEMPLATE_PATTERN = Pattern.quote("{{accountId}}");

  private final ObjectMapper objectMapper;
  private final AmazonClientProvider amazonClientProvider;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final InstanceTerminationConfigurationProperties properties;
  private final Provider<AwsEurekaSupport> discoverySupport;

  InstanceTerminationLifecycleAgentProvider(ObjectMapper objectMapper,
                                            AmazonClientProvider amazonClientProvider,
                                            AccountCredentialsProvider accountCredentialsProvider,
                                            InstanceTerminationConfigurationProperties properties,
                                            Provider<AwsEurekaSupport> discoverySupport) {
    this.objectMapper = objectMapper;
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.properties = properties;
    this.discoverySupport = discoverySupport;
  }

  @Override
  public boolean supports(String providerName) {
    return providerName.equalsIgnoreCase(AwsProvider.PROVIDER_NAME);
  }

  @Override
  public Collection<Agent> agents() {
    NetflixAmazonCredentials credentials = (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(
      properties.getAccountName()
    );

    // an agent for each region in the specified account
    return credentials.getRegions().stream()
      .map(region -> new InstanceTerminationLifecycleAgent(
        objectMapper,
        amazonClientProvider,
        accountCredentialsProvider,
        new InstanceTerminationConfigurationProperties(
          properties.getAccountName(),
          properties
            .getQueueARN()
            .replaceAll(REGION_TEMPLATE_PATTERN, region.getName())
            .replaceAll(ACCOUNT_ID_TEMPLATE_PATTERN, credentials.getAccountId()),
          properties.getTopicARN()
            .replaceAll(REGION_TEMPLATE_PATTERN, region.getName())
            .replaceAll(ACCOUNT_ID_TEMPLATE_PATTERN, credentials.getAccountId()),
          properties.getMaxMessagesPerCycle(),
          properties.getVisibilityTimeout(),
          properties.getWaitTimeSeconds()
        ),
        discoverySupport
      ))
      .collect(Collectors.toList());
  }
}
