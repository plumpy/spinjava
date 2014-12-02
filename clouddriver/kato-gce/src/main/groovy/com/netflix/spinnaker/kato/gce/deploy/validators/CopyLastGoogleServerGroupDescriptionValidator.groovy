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

package com.netflix.spinnaker.kato.gce.deploy.validators

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.gce.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.gce.security.GoogleCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("copyLastGoogleServerGroupDescriptionValidator")
class CopyLastGoogleServerGroupDescriptionValidator extends DescriptionValidator<BasicGoogleDeployDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, BasicGoogleDeployDescription description, Errors errors) {
    def credentials = null

    // TODO(duftler): Once we're happy with this routine, move it to a common base class.
    if (!description.accountName) {
      errors.rejectValue "credentials", "basicGoogleDeployDescription.credentials.empty"
    } else {
      credentials = accountCredentialsProvider.getCredentials(description.accountName)

      if (!(credentials?.credentials instanceof GoogleCredentials)) {
        errors.rejectValue("credentials", "basicGoogleDeployDescription.credentials.invalid")
      }
    }
  }
}
