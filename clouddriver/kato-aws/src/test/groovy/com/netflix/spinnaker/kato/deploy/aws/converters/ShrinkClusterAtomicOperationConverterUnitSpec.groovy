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


package com.netflix.spinnaker.kato.deploy.aws.converters

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.deploy.aws.description.ShrinkClusterDescription
import com.netflix.spinnaker.kato.deploy.aws.ops.ShrinkClusterAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class ShrinkClusterAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  ShrinkClusterAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new ShrinkClusterAtomicOperationConverter()
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(NetflixAmazonCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "shrinkClusterDescription type returns ShrinkClusterDescription and ShrinkClusterAtomicOperation"() {
    setup:
    def input = [application: "asgard", clusterName: "asgard-test", regions: ["us-west-1"], credentials: "test"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof ShrinkClusterDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof ShrinkClusterAtomicOperation
  }
}
