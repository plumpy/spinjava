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


package com.netflix.spinnaker.kato.aws.deploy.converters

import com.netflix.spinnaker.clouddriver.aws.Amazon
import com.netflix.spinnaker.kato.aws.deploy.description.DestroyAsgDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.DestroyAsgAtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperationDescription
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Amazon
@AtomicOperationDescription("destroyServerGroupDescription")
@Component("destroyAsgDescription")
class DestroyAsgAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new DestroyAsgAtomicOperation(convertDescription(input))
  }

  @Override
  DestroyAsgDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, DestroyAsgDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
