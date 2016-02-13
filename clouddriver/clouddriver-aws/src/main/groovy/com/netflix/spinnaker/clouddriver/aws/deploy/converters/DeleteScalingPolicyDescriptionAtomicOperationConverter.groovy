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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DeleteScalingPolicyAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.UpsertScalingPolicyAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component('deleteScalingPolicyDescription')
class DeleteScalingPolicyDescriptionAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Autowired
  ObjectMapper objectMapper

  @Override
  DeleteScalingPolicyAtomicOperation convertOperation(Map input) {
    new DeleteScalingPolicyAtomicOperation(convertDescription(input))
  }

  @Override
  DeleteScalingPolicyDescription convertDescription(Map input) {
    def converted = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).convertValue(input, DeleteScalingPolicyDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
