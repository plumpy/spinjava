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

import com.netflix.spinnaker.kato.config.KatoAWSConfig
import com.netflix.spinnaker.kato.deploy.aws.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.kato.deploy.aws.description.UpsertSecurityGroupDescription.SecurityGroupIngress
import com.netflix.spinnaker.kato.model.aws.SecurityGroupNotFoundException
import com.netflix.spinnaker.kato.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.services.SecurityGroupService
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertSecurityGroupDescriptionValidatorSpec extends Specification {

  @Subject validator = new UpsertSecurityGroupDescriptionValidator()

  @Shared
  SecurityGroupService securityGroupService

  @Shared
  Errors errors

  def description = new UpsertSecurityGroupDescription().with {
    name = "foo"
    description = "desc"
    securityGroupIngress = [
      new SecurityGroupIngress().with {
        name = "bar"
        startPort = 111
        endPort = 111
        type = UpsertSecurityGroupDescription.IngressType.tcp
        it
      }
    ]
    it
  }

  def setup() {
    validator.awsConfigurationProperties = new KatoAWSConfig.AwsConfigurationProperties(regions: ["us-west-1"])
    securityGroupService = Mock(SecurityGroupService)
    errors = Mock(Errors)
    def regionScopedProviderFactory = Mock(RegionScopedProviderFactory)
    def regionScopedProvider = Mock(RegionScopedProviderFactory.RegionScopedProvider)
    regionScopedProvider.getSecurityGroupService() >> securityGroupService
    regionScopedProviderFactory.forRegion(_, _) >> regionScopedProvider
    validator.regionScopedProviderFactory = regionScopedProviderFactory
  }

  void "should reject unknwon security groups"() {
    when:
    validator.validate(_, description, errors)

    then:
    1 * securityGroupService.getSecurityGroupIds(_) >> { throw new SecurityGroupNotFoundException() }
    1 * errors.rejectValue("securityGroupIngress", _)
  }

  void "region is validates against configuration"() {
    setup:
    def description = getDescription()
    description.region = "us-east-5"

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("regions", _)

    when:
    description.region = validator.awsConfigurationProperties.regions[0]
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("regions", _)
  }

}
