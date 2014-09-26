/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.deploy.gce.converters

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.kato.deploy.gce.description.DeleteGoogleReplicaPoolDescription
import com.netflix.spinnaker.kato.deploy.gce.ops.DeleteGoogleReplicaPoolAtomicOperation
import com.netflix.spinnaker.kato.security.gce.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification

class DeleteGoogleReplicaPoolAtomicOperationConverterUnitSpec extends Specification {
  private static final APPLICATION = "spinnaker"
  private static final STACK = "spinnaker-test"
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  DeleteGoogleReplicaPoolAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeleteGoogleReplicaPoolAtomicOperationConverter()
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "deleteGoogleReplicaPoolDescription type returns DeleteGoogleReplicaPoolDescription and DeleteGoogleReplicaPoolAtomicOperation"() {
    setup:
      def input = [application: APPLICATION,
                   stack: STACK,
                   zone: ZONE,
                   accountName: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof DeleteGoogleReplicaPoolDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeleteGoogleReplicaPoolAtomicOperation
  }
}
