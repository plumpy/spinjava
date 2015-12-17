/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.kato.cf.deploy.converters

import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.kato.cf.deploy.description.DestroyCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.cf.deploy.ops.DestroyCloudFoundryServerGroupAtomicOperation
import org.springframework.stereotype.Component

@CloudFoundryOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyCloudFoundryServerGroupDescription")
class DestroyCloudFoundryServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new DestroyCloudFoundryServerGroupAtomicOperation(convertDescription(input))
  }

  @Override
  Object convertDescription(Map input) {
    new DestroyCloudFoundryServerGroupDescription([
      serverGroupName : input.serverGroupName,
      zone            : input.containsKey('zones') ? input.zones[0] : input.containsKey('zone') ? input.zone : input.region,
      credentials     : getCredentialsObject(input.credentials as String)
    ])
  }
}
