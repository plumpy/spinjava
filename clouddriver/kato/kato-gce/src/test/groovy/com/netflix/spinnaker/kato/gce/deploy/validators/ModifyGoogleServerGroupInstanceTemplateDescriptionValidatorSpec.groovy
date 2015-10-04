/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.validators

import com.netflix.spinnaker.amos.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.amos.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kato.gce.deploy.description.ModifyGoogleServerGroupInstanceTemplateDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class ModifyGoogleServerGroupInstanceTemplateDescriptionValidatorSpec extends Specification {
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final ZONE = "us-central1-b"
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final DISK_TYPE = "pd-standard"
  private static final DISK_SIZE_GB = 10
  private static final INSTANCE_METADATA = [
    "startup-script": "sudo apt-get update",
    "some-key": "some-value"
  ]
  private static final TAGS = ["some-tag-1", "some-tag-2"]
  private static final NETWORK = "default"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ModifyGoogleServerGroupInstanceTemplateDescriptionValidator validator

  void setupSpec() {
    validator = new ModifyGoogleServerGroupInstanceTemplateDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = Mock(GoogleNamedAccountCredentials)
    credentials.getName() >> ACCOUNT_NAME
    credentials.getCredentials() >> new GoogleCredentials(null, null, null, null, null)
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with minimum proper description inputs"() {
    setup:
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                               zone: ZONE,
                                                                               accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with all optional inputs"() {
    setup:
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                               zone: ZONE,
                                                                               image: IMAGE,
                                                                               instanceType: INSTANCE_TYPE,
                                                                               diskType: DISK_TYPE,
                                                                               diskSizeGb: DISK_SIZE_GB,
                                                                               instanceMetadata: INSTANCE_METADATA,
                                                                               tags: TAGS,
                                                                               network: NETWORK,
                                                                               accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails validation"() {
    setup:
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      // The point of being explicit here is only to verify the context and property names.
      // We'll assume the policies are covered by the tests on the underlying validator.
      1 * errors.rejectValue('serverGroupName', "modifyGoogleServerGroupInstanceTemplateDescription.serverGroupName.empty")
      1 * errors.rejectValue('zone', "modifyGoogleServerGroupInstanceTemplateDescription.zone.empty")
      1 * errors.rejectValue('credentials', "modifyGoogleServerGroupInstanceTemplateDescription.credentials.empty")
  }
}
