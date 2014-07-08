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
import com.netflix.spinnaker.kato.deploy.aws.description.CreateNetworkInterfaceDescription
import com.netflix.spinnaker.kato.model.aws.AwsNetworkInterface
import org.springframework.validation.Errors
import spock.lang.Specification

class CreateNetworkInterfaceDescriptionValidatorSpec extends Specification {

  CreateNetworkInterfaceDescriptionValidator validator = new CreateNetworkInterfaceDescriptionValidator(
    awsConfigurationProperties: new KatoAWSConfig.AwsConfigurationProperties(regions: ["us-east-1", "us-west-1"]))

  void "pass validation with proper description inputs"() {
    setup:
    def description = new CreateNetworkInterfaceDescription(
      availabilityZonesGroupedByRegion: [
        "us-west-1": ["us-west-1a", "us-west-1b"],
        "us-east-1": ["us-east-1b", "us-east-1c"]
      ],
      vpcId: "vpc-1badd00d",
      subnetType: "internal",
      networkInterface: new AwsNetworkInterface(
        description: "internal Asgard",
        securityGroupNames: ["sg-12345678", "sg-87654321"],
        primaryPrivateIpAddress: "127.0.0.1",
        secondaryPrivateIpAddresses: ["127.0.0.2", "127.0.0.3"]
      )
    )
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "null input fails validation"() {
    setup:
    def description = new CreateNetworkInterfaceDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    2 * errors.rejectValue('regions', _)
    1 * errors.rejectValue('availabilityZones', _)
    1 * errors.rejectValue('subnetType', _)
    0 * _
  }

  void "unconfigured region fails validation"() {
    setup:
    def description = new CreateNetworkInterfaceDescription(availabilityZonesGroupedByRegion: ["eu-west-5": []])
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("regions", "createNetworkInterfaceDescription.regions.not.configured")
  }
}
