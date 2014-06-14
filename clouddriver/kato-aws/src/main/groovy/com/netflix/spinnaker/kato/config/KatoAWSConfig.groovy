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


package com.netflix.spinnaker.kato.config

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.deploy.aws.userdata.NullOpUserDataProvider
import com.netflix.spinnaker.kato.deploy.aws.userdata.UserDataProvider
import com.netflix.spinnaker.kato.security.NamedAccountCredentialsHolder
import com.netflix.spinnaker.kato.security.aws.AmazonRoleAccountCredentials
import com.netflix.spinnaker.kato.security.aws.BasicAmazonNamedAccountCredentials
import com.netflix.spinnaker.kato.security.aws.BastionCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Configuration
class KatoAWSConfig {

  @Bean
  @ConditionalOnMissingBean(UserDataProvider)
  UserDataProvider userDataProvider() {
    new NullOpUserDataProvider()
  }

  @Bean
  AmazonClientProvider amazonClientProvider() {
    new AmazonClientProvider()
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
    NamedAccountCredentialsHolder namedAccountCredentialsHolder

    @Autowired
    BastionConfiguration bastionConfiguration

    @Autowired
    AwsConfigurationProperties awsConfigurationProperties

    @PostConstruct
    void init() {
      def provider = new BastionCredentialsProvider(bastionConfiguration.user, bastionConfiguration.host, bastionConfiguration.port, bastionConfiguration.proxyCluster,
        bastionConfiguration.proxyRegion, awsConfigurationProperties.accountIamRole)
      configureAccount provider, namedAccountCredentialsHolder, awsConfigurationProperties
    }
  }

  @Configuration
  @ConditionalOnExpression('!${bastion.enabled:false}')
  static class AmazonCredentialsInitializer {
    @Autowired
    AWSCredentialsProvider awsCredentialsProvider

    @Autowired
    NamedAccountCredentialsHolder namedAccountCredentialsHolder

    @Autowired
    AwsConfigurationProperties awsConfigurationProperties

    @Value('${default.account.env:default}')
    String defaultEnv

    @PostConstruct
    void init() {
      if (!awsConfigurationProperties.accounts) {
        namedAccountCredentialsHolder.put(defaultEnv, new BasicAmazonNamedAccountCredentials(awsCredentialsProvider, defaultEnv, null))
      } else {
        configureAccount awsCredentialsProvider, namedAccountCredentialsHolder, awsConfigurationProperties
      }
    }
  }

  private static void configureAccount(AWSCredentialsProvider provider, NamedAccountCredentialsHolder namedAccountCredentialsHolder, AwsConfigurationProperties awsConfigurationProperties) {
    for (account in awsConfigurationProperties.accounts) {
      namedAccountCredentialsHolder.put(account.name, new AmazonRoleAccountCredentials(provider, account.accountId, account.name, awsConfigurationProperties.assumeRole, account.edda, account.regions))
    }
  }

  static class DeployDefaults {
    String iamRole
    String keyPair
  }

  static class ManagedAccount {
    String name
    String accountId
    String edda
    List<String> regions
  }

  @Component
  @ConfigurationProperties("aws")
  static class AwsConfigurationProperties {
    List<String> regions
    DeployDefaults defaults = new DeployDefaults()
    // This is the IAM Role that Kato will be deployed under
    String accountIamRole
    // This is the IAM Role that Kato will assume within ManagedAccounts
    String assumeRole
    // These are accounts that have been configured with permissions under the above assumeRole for Kato to perform operations
    List<ManagedAccount> accounts
  }
}
