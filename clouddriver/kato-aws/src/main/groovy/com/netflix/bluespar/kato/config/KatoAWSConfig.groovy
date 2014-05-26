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

package com.netflix.bluespar.kato.config

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.bluespar.amazon.security.AmazonClientProvider
import com.netflix.bluespar.kato.deploy.aws.userdata.NullOpUserDataProvider
import com.netflix.bluespar.kato.deploy.aws.userdata.UserDataProvider
import com.netflix.bluespar.kato.security.NamedAccountCredentialsHolder
import com.netflix.bluespar.kato.security.aws.AmazonNamedAccountCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
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
  AmazonClientProvider amazonClientProvider(@Value('${edda.host.format:#{null}}') String edda) {
    new AmazonClientProvider(edda)
  }

  @Component
  static class AmazonCredentialsInitializer {
    @Autowired
    AWSCredentialsProvider awsCredentialsProvider

    @Autowired
    NamedAccountCredentialsHolder namedAccountCredentialsHolder

    @Value('${default.account.env:default}')
    String defaultEnv

    @PostConstruct
    void init() {
      namedAccountCredentialsHolder.put(defaultEnv, new AmazonNamedAccountCredentials(awsCredentialsProvider, defaultEnv))
    }
  }

  static class DeployDefaults {
    String iamRole = "BaseIAMRole"
    String keyPair = "nf-test-keypair-a"
  }

  @Component
  @ConfigurationProperties(value = "aws", locations = "classpath:aws.yml")
  static class AwsConfigurationProperties {
    List<String> regions
    DeployDefaults defaults = new DeployDefaults()
  }
}
