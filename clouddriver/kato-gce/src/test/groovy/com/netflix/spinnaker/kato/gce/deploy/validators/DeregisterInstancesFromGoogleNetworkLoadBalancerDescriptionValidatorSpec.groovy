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
import com.netflix.spinnaker.kato.gce.deploy.description.DeregisterInstancesFromGoogleNetworkLoadBalancerDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class DeregisterInstancesFromGoogleNetworkLoadBalancerDescriptionValidatorSpec extends Specification {
  private static final LOAD_BALANCER_NAME = "spinnaker-test-loadbalancer"
  private static final REGION = "us-central1"
  private static final ACCOUNT_NAME = "auto"
  private static final INSTANCE_IDS = ["my-app7-dev-v000-instance1", "my-app7-dev-v000-instance2"]

  @Shared
  DeregisterInstancesFromGoogleNetworkLoadBalancerDescriptionValidator validator

  void setupSpec() {
    validator = new DeregisterInstancesFromGoogleNetworkLoadBalancerDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = Mock(GoogleNamedAccountCredentials)
    credentials.getName() >> ACCOUNT_NAME
    credentials.getCredentials() >> new GoogleCredentials()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new DeregisterInstancesFromGoogleNetworkLoadBalancerDescription(
        networkLoadBalancerName: LOAD_BALANCER_NAME,
        instanceIds: INSTANCE_IDS,
        region: REGION,
        accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails validation"() {
    setup:
      def description = new DeregisterInstancesFromGoogleNetworkLoadBalancerDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)
    then:
      // The point of being explicit here is only to verify the context and property names.
      // We'll assume the policies are covered by the tests on the underlying validator.
      1 * errors.rejectValue(
        'credentials', 'deregisterInstancesFromGoogleNetworkLoadBalancerDescription.credentials.empty')
      1 * errors.rejectValue(
        'networkLoadBalancerName',
        'deregisterInstancesFromGoogleNetworkLoadBalancerDescription.networkLoadBalancerName.empty')
      1 * errors.rejectValue(
        'region', 'deregisterInstancesFromGoogleNetworkLoadBalancerDescription.region.empty')
      1 * errors.rejectValue('' +
        'instanceIds', 'deregisterInstancesFromGoogleNetworkLoadBalancerDescription.instanceIds.empty')
  }
}
