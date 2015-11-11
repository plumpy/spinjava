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

import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.kato.config.GceConfig
import com.netflix.spinnaker.kato.gce.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.gce.model.GoogleDisk
import com.netflix.spinnaker.kato.gce.model.GoogleInstanceTypeDisk
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class BasicGoogleDeployDescriptionValidatorSpec extends Specification {
  private static final APPLICATION = "spinnaker"
  private static final STACK = "spinnaker-test"
  private static final FREE_FORM_DETAILS = "detail"
  private static final TARGET_SIZE = 3
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final INSTANCE_TYPE_DISK = new GoogleInstanceTypeDisk(instanceType: INSTANCE_TYPE, supportsLocalSSD: false)
  private static final DISK_TYPE = "pd-standard"
  private static final DISK_SIZE_GB = 10
  private static final DISK_PD_STANDARD = new GoogleDisk(type: DISK_TYPE, sizeGb: DISK_SIZE_GB)
  private static final DISK_PD_SSD = new GoogleDisk(type: "pd-ssd", sizeGb: 125)
  private static final DISK_LOCAL_SSD = new GoogleDisk(type: "local-ssd", sizeGb: 375)
  private static final DISK_LOCAL_SSD_INVALID_SIZE = new GoogleDisk(type: "local-ssd", sizeGb: 500)
  private static final DISK_LOCAL_SSD_NO_AUTO_DELETE = new GoogleDisk(type: "local-ssd", sizeGb: 375, autoDelete: false)
  private static final DISK_NO_SIZE = new GoogleDisk(type: "pd-ssd")
  private static final DISK_NEGATIVE_SIZE = new GoogleDisk(type: "pd-ssd", sizeGb: -5)
  private static final DISK_ZERO_SIZE = new GoogleDisk(type: "pd-ssd", sizeGb: 0)
  private static final DISK_TOO_SMALL_SIZE = new GoogleDisk(type: "pd-ssd", sizeGb: 7)
  private static final DISK_NO_TYPE = new GoogleDisk(sizeGb: 125)
  private static final ZONE = "us-central1-b"
  private static final TAGS = ["some-tag-1", "some-tag-2", "some-tag-3"]
  private static final ACCOUNT_NAME = "auto"

  @Shared
  BasicGoogleDeployDescriptionValidator validator

  void setupSpec() {
    def gceDeployDefaults = new GceConfig.DeployDefaults(instanceTypeDisks: [INSTANCE_TYPE_DISK])
    validator = new BasicGoogleDeployDescriptionValidator(gceDeployDefaults: gceDeployDefaults)
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = Mock(GoogleNamedAccountCredentials)
    credentials.getName() >> ACCOUNT_NAME
    credentials.getCredentials() >> new GoogleCredentials(null, null)
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new BasicGoogleDeployDescription(application: APPLICATION,
                                                         stack: STACK,
                                                         targetSize: TARGET_SIZE,
                                                         image: IMAGE,
                                                         instanceType: INSTANCE_TYPE,
                                                         disks: [DISK_PD_STANDARD],
                                                         zone: ZONE,
                                                         accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with proper description inputs and free-form details"() {
    setup:
      def description = new BasicGoogleDeployDescription(application: APPLICATION,
                                                         stack: STACK,
                                                         freeFormDetails: FREE_FORM_DETAILS,
                                                         targetSize: TARGET_SIZE,
                                                         image: IMAGE,
                                                         instanceType: INSTANCE_TYPE,
                                                         zone: ZONE,
                                                         accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with proper description inputs and tags"() {
    setup:
      def description = new BasicGoogleDeployDescription(application: APPLICATION,
                                                         stack: STACK,
                                                         freeFormDetails: FREE_FORM_DETAILS,
                                                         targetSize: TARGET_SIZE,
                                                         image: IMAGE,
                                                         instanceType: INSTANCE_TYPE,
                                                         zone: ZONE,
                                                         tags: TAGS,
                                                         accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
     validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "invalid targetSize fails validation"() {
    setup:
      def description = new BasicGoogleDeployDescription(targetSize: -1)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("targetSize", "basicGoogleDeployDescription.targetSize.negative")
  }

  void "invalid disk sizeGb fails validation"() {
    setup:
      def errors = Mock(Errors)

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_NO_SIZE]), errors)

    then:
      1 * errors.rejectValue("disks", "basicGoogleDeployDescription.disk0.sizeGb.empty")

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_NEGATIVE_SIZE]), errors)

    then:
      1 * errors.rejectValue("disk0.sizeGb", "basicGoogleDeployDescription.disk0.sizeGb.negative")

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_ZERO_SIZE]), errors)

    then:
      1 * errors.rejectValue("disks",
                             "basicGoogleDeployDescription.disk0.sizeGb.invalidSize",
                             "Persistent disks must be at least 10GB.")

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_TOO_SMALL_SIZE]), errors)

    then:
      1 * errors.rejectValue("disks",
                             "basicGoogleDeployDescription.disk0.sizeGb.invalidSize",
                             "Persistent disks must be at least 10GB.")

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_LOCAL_SSD_INVALID_SIZE]), errors)

    then:
      1 * errors.rejectValue("disks",
                             "basicGoogleDeployDescription.disk0.sizeGb.invalidSize",
                             "Local SSD disks must be exactly 375GB.")
  }

  void "missing disk type fails validation"() {
    setup:
      def errors = Mock(Errors)

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_NO_TYPE]), errors)

    then:
      1 * errors.rejectValue("disks", "basicGoogleDeployDescription.disk0.type.empty")
  }

  void "invalid number of persistent disks fails validation"() {
    setup:
      def errors = Mock(Errors)

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_LOCAL_SSD]), errors)

    then:
      1 * errors.rejectValue("disks",
                             "basicGoogleDeployDescription.disks.missingPersistentDisk",
                             "A persistent boot disk is required.")

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_LOCAL_SSD, DISK_LOCAL_SSD]), errors)

    then:
      1 * errors.rejectValue("disks",
                             "basicGoogleDeployDescription.disks.missingPersistentDisk",
                             "A persistent boot disk is required.")

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_PD_STANDARD, DISK_PD_SSD]), errors)

    then:
      1 * errors.rejectValue("disks",
                             "basicGoogleDeployDescription.disks.tooManyPersistentDisks",
                             "Cannot specify more than one persistent disk.")

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_PD_STANDARD, DISK_LOCAL_SSD, DISK_PD_SSD]), errors)

    then:
      1 * errors.rejectValue("disks",
                             "basicGoogleDeployDescription.disks.tooManyPersistentDisks",
                             "Cannot specify more than one persistent disk.")
  }

  void "invalid local ssd settings fails validation"() {
    setup:
      def errors = Mock(Errors)

    when:
      validator.validate([], new BasicGoogleDeployDescription(disks: [DISK_LOCAL_SSD_NO_AUTO_DELETE]), errors)

    then:
      1 * errors.rejectValue("disks",
                             "basicGoogleDeployDescription.disk0.autoDelete.required",
                             "Local SSD disks must have auto-delete set.")

    when:
      validator.validate([], new BasicGoogleDeployDescription(instanceType: INSTANCE_TYPE, disks: [DISK_LOCAL_SSD]), errors)

    then:
      1 * errors.rejectValue("disks",
                             "basicGoogleDeployDescription.disk0.type.localSSDUnsupported",
                             "Instance type $INSTANCE_TYPE does not support Local SSD.")
  }

  void "null input fails validation"() {
    setup:
      def description = new BasicGoogleDeployDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('application', _)
      1 * errors.rejectValue('image', _)
      1 * errors.rejectValue('instanceType', _)
      1 * errors.rejectValue('zone', _)
  }
}
