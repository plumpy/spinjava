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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteScalingPolicyDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("deleteScalingPolicyDescriptionValidator")
class DeleteScalingPolicyDescriptionValidator extends AmazonDescriptionValidationSupport<DeleteScalingPolicyDescription> {
  @Override
  void validate(List priorDescriptions, DeleteScalingPolicyDescription description, Errors errors) {
    validateRegions(description, [description.region], "deleteScalingPolicyDescription", errors)

    if (!description.asgName) {
      rejectNull "asgName", errors
    }

    if (!description.name) {
      rejectNull "policyName", errors
    }

  }

  static void rejectNull(String field, Errors errors) {
    errors.rejectValue(field, "deleteScalingPolicyDescription.${field}.not.nullable")
  }
}
