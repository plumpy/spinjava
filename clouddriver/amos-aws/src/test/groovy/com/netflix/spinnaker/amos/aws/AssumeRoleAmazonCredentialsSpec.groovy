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

package com.netflix.spinnaker.amos.aws

import com.netflix.spinnaker.amos.YamlAccountCredentialsFactory
import spock.lang.Shared
import spock.lang.Specification

class AssumeRoleAmazonCredentialsSpec extends Specification {

  @Shared factory = new YamlAccountCredentialsFactory()

  void "should provide assume role and session name from yaml config"() {
    given:
      def obj = factory.load(yaml, AssumeRoleAmazonCredentials)

    expect:
      obj instanceof AssumeRoleAmazonCredentials

    and:
      obj.assumeRole == "role/asgard"
      obj.sessionName == "Spinnaker"

    where:
      yaml = """\
        |assumeRole: role/asgard
        |sessionName: Spinnaker
      """.stripMargin()
  }
}
