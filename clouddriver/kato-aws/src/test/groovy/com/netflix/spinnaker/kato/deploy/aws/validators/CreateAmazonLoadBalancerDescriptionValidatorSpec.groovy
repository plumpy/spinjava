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


package com.netflix.spinnaker.kato.deploy.aws.validators

import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.config.KatoAWSConfig
import com.netflix.spinnaker.kato.deploy.aws.description.CreateAmazonLoadBalancerDescription
import com.netflix.spinnaker.kato.security.aws.AmazonRoleAccountCredentials
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class CreateAmazonLoadBalancerDescriptionValidatorSpec extends Specification {

  @Shared
  CreateAmazonLoadBalancerDescriptionValidator validator

  CreateAmazonLoadBalancerDescription description

  void setupSpec() {
    validator = new CreateAmazonLoadBalancerDescriptionValidator()
    validator.awsConfigurationProperties = new KatoAWSConfig.AwsConfigurationProperties(
      regions: ["us-west-1"],
      accounts: [
        new KatoAWSConfig.ManagedAccount(
          name: 'test',
          regions: [
            new AmazonRoleAccountCredentials.AwsRegion(
              name: "us-west-1",
              availabilityZones: ["us-west-1a"]
            )
          ]
        )
      ])
  }

  void setup() {
    description = new CreateAmazonLoadBalancerDescription(credentials: new AmazonCredentials(null, 'test'))
  }

  void "empty parameters fails validation"() {
    setup:
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("clusterName", _)
    1 * errors.rejectValue("availabilityZones", _)
    1 * errors.rejectValue("listeners", _)
  }

  void "unconfigured region is rejected"() {
    setup:
    description.availabilityZones = ["us-west-5": ["us-west-5a"]]
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("availabilityZones", _)
  }

  void "availability zone not configured for account is rejected"() {
    setup:
    description.availabilityZones = ["us-west-1": ["us-west-1b"]]
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("availabilityZones", _)

  }

  void "subnetType supercedes availabilityZones"() {
    setup:
    description.subnetType = "internal"
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("availabilityZones", _)
  }

  void "availabilityZones if not subnetType"() {
    setup:
    description.availabilityZones = ["us-west-1": ["us-west-1a"]]
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("subnetType", _)
    0 * errors.rejectValue("availabilityZones", _)
  }

  void "invalid subnet fails validation"() {
    setup:
    description.subnetType = "lol"
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("subnetType", _)
  }
}
