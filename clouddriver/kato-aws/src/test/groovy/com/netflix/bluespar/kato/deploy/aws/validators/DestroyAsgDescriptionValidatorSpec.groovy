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

import com.netflix.bluespar.kato.config.KatoAWSConfig
import com.netflix.bluespar.kato.deploy.aws.description.DestroyAsgDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class DestroyAsgDescriptionValidatorSpec extends Specification {
  @Shared
  DestroyAsgDescriptionValidator validator

  void setupSpec() {
    validator = new DestroyAsgDescriptionValidator()
    validator.awsConfigurationProperties = new KatoAWSConfig.AwsConfigurationProperties(regions: ["us-west-1"])
  }

  void "empty description fails validation"() {
    setup:
    def description = new DestroyAsgDescription()
    def errors = Mock(Errors)
    when:
    validator.validate([], description, errors)
    then:
    1 * errors.rejectValue("asgName", _)
    1 * errors.rejectValue("regions", _)
    0 * errors._
  }

  void "region is validates against configuration"() {
    setup:
    def description = new DestroyAsgDescription(regions: ["us-east-5"])
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("regions", _)

    when:
    description.regions = validator.awsConfigurationProperties.regions
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("regions", _)
  }
}
