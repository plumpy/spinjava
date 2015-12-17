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

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.config.GceConfig
import com.netflix.spinnaker.kato.gce.deploy.description.ModifyGoogleServerGroupInstanceTemplateDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("modifyGoogleServerGroupInstanceTemplateDescriptionValidator")
class ModifyGoogleServerGroupInstanceTemplateDescriptionValidator extends DescriptionValidator<ModifyGoogleServerGroupInstanceTemplateDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  private GceConfig.DeployDefaults gceDeployDefaults

  @Override
  void validate(List priorDescriptions, ModifyGoogleServerGroupInstanceTemplateDescription description, Errors errors) {
    def helper = new StandardGceAttributeValidator("modifyGoogleServerGroupInstanceTemplateDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateInstanceTypeDisks(gceDeployDefaults.determineInstanceTypeDisk(description.instanceType),
                                     description.disks)
    helper.validateAuthScopes(description.authScopes)
    helper.validateZone(description.zone)
    helper.validateServerGroupName(description.serverGroupName)
  }
}
