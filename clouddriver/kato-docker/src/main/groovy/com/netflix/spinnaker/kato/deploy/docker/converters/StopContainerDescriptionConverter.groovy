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

package com.netflix.spinnaker.kato.deploy.docker.converters

import com.netflix.spinnaker.kato.deploy.docker.description.StopContainerDescription
import com.netflix.spinnaker.kato.deploy.docker.op.StopContainerAtomicOperation
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("stopContainerDescription")
class StopContainerDescriptionConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  StopContainerAtomicOperation convertOperation(Map input) {
    new StopContainerAtomicOperation(convertDescription(input))
  }

  @Override
  StopContainerDescription convertDescription(Map input) {
    input.credentials = getCredentialsObject(input.credentials as String)
    new StopContainerDescription(input)
  }
}
