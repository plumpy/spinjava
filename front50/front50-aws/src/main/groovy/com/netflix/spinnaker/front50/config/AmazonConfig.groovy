/*
 * Copyright 2014 Netflix, Inc.
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



package com.netflix.spinnaker.front50.config

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import com.netflix.spinnaker.front50.security.NamedAccountProvider
import com.netflix.spinnaker.front50.security.aws.AmazonRoleAccountCredentials
import com.netflix.spinnaker.front50.security.aws.BasicAmazonNamedAccountCredentials
import com.netflix.spinnaker.front50.security.aws.BastionCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

/**
 * Created by aglover on 4/23/14.
 */
@Configuration
class AmazonConfig {

  @Bean
  AmazonSimpleDB awsSimpleDBClient(AWSCredentialsProvider awsCredentialsProvider) {
    new AmazonSimpleDBClient(awsCredentialsProvider)
  }

  @Component
  @ConfigurationProperties("bastion")
  static class BastionConfiguration {
    Boolean enabled
    String host
    String user
    Integer port
    String proxyCluster
    String proxyRegion
  }

  @Configuration
  @ConditionalOnExpression('${bastion.enabled:false}')
  static class BastionCredentialsInitializer {
    @Autowired
    NamedAccountProvider namedAccountProvider

    @Autowired
    BastionConfiguration bastionConfiguration

    @Autowired
    AwsConfigurationProperties awsConfigurationProperties

    @PostConstruct
    void init() {
      def provider = new BastionCredentialsProvider(bastionConfiguration.user, bastionConfiguration.host, bastionConfiguration.port, bastionConfiguration.proxyCluster,
        bastionConfiguration.proxyRegion, awsConfigurationProperties.accountIamRole)
      configureAccount provider, namedAccountProvider, awsConfigurationProperties
    }
  }

  @Configuration
  @ConditionalOnExpression('!${bastion.enabled:false}')
  static class AmazonCredentialsInitializer {
    @Autowired
    AWSCredentialsProvider awsCredentialsProvider

    @Autowired
    NamedAccountProvider namedAccountProvider

    @Autowired
    AwsConfigurationProperties awsConfigurationProperties

    @Value('${default.account.env:default}')
    String defaultEnv

    @PostConstruct
    void init() {
      if (!awsConfigurationProperties.accounts) {
        namedAccountProvider.put(new BasicAmazonNamedAccountCredentials(awsCredentialsProvider, defaultEnv, awsConfigurationProperties.defaultSimpleDBDomain))
      } else {
        configureAccount awsCredentialsProvider, namedAccountProvider, awsConfigurationProperties
      }
    }
  }

  private static void configureAccount(AWSCredentialsProvider provider, NamedAccountProvider namedAccountCredentialsHolder, AwsConfigurationProperties awsConfigurationProperties) {
    for (account in awsConfigurationProperties.accounts) {
      namedAccountCredentialsHolder.put(new AmazonRoleAccountCredentials(provider, account.simpleDBDomain ?: awsConfigurationProperties.defaultSimpleDBDomain, account.accountId, account.name,
        awsConfigurationProperties.assumeRole))
    }
  }

  static class ManagedAccount {
    String name
    String accountId
    String simpleDBDomain
    boolean defaultAccount
  }

  @Component
  @ConfigurationProperties("aws")
  static class AwsConfigurationProperties {
    String accountIamRole
    String assumeRole
    String defaultSimpleDBDomain = "RESOURCE_REGISTRY"
    List<ManagedAccount> accounts
  }

}