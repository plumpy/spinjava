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

package com.netflix.spinnaker.front50.model.application

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.front50.config.AmazonConfig
import spock.lang.Specification
import spock.lang.Subject

class AmazonApplicationDAOProviderSpec extends Specification {

  @Subject provider = new AmazonApplicationDAOProvider()

  def config = new AmazonConfig.AwsConfigurationProperties()

  void setup() {
    provider.awsConfigurationProperties = config
  }

  void "should fail when not an AmazonAccount object"() {
    expect:
    !provider.supports(AccountCredentials)
  }

  void "should return an AmazonApplicationDAO for an AmazonAccount object"() {
    setup:
    def credProvider = Mock(AWSCredentialsProvider)

    when:
    def dao = provider.getForAccount(new AmazonCredentials(new AmazonCredentials("test", "12345", null, [new AmazonCredentials.AWSRegion("us-west-1", ["us-west-1a"])]), credProvider))

    then:
    dao instanceof AmazonApplicationDAO
  }
}
