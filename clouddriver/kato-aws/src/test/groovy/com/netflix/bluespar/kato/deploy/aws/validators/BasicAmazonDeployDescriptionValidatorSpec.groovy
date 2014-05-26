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

package com.netflix.bluespar.kato.deploy.aws.validators

import com.netflix.bluespar.amazon.security.AmazonCredentials
import com.netflix.bluespar.kato.config.KatoAWSConfig
import com.netflix.bluespar.kato.deploy.aws.description.BasicAmazonDeployDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class BasicAmazonDeployDescriptionValidatorSpec extends Specification {

  @Shared
  BasicAmazonDeployDescriptionValidator validator

  void setupSpec() {
    validator = new BasicAmazonDeployDescriptionValidator(awsConfigurationProperties: new KatoAWSConfig.AwsConfigurationProperties(regions: ["us-west-1"]))
  }

  void "null input fails valiidation"() {
    setup:
    def description = new BasicAmazonDeployDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    5 * errors.rejectValue(_, _)
  }

  void "invalid capacity fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: Mock(AmazonCredentials), availabilityZones: ["us-west-1": []])
    description.capacity.min = 5
    description.capacity.max = 3
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', 'basicAmazonDeployDescription.capacity.transposed', ['5', '3'], 'Capacity min and max appear transposed')

    when:
    description.capacity.min = 3
    description.capacity.max = 5
    description.capacity.desired = 7
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', 'basicAmazonDeployDescription.desired.capacity.not.in.range', ['3', '5', '7'], 'Desired capacity is not within min/max range')
  }

  void "unconfigured region fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: Mock(AmazonCredentials), availabilityZones: ["eu-west-5": []])
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("availabilityZones", "basicAmazonDeployDescription.region.not.configured", ["eu-west-5"], 'Region eu-west-5 not configured')
  }
}
