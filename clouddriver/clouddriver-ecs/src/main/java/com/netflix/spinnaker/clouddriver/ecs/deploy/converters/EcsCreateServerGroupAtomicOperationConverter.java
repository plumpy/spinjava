/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.converters;

import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.CreateServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.stereotype.Component;

@EcsOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("ecsCreateServerGroup")
public class EcsCreateServerGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new CreateServerGroupAtomicOperation(convertDescription(input));
  }

  @Override
  public CreateServerGroupDescription convertDescription(Map input) {
    CreateServerGroupDescription converted =
        getObjectMapper().convertValue(input, CreateServerGroupDescription.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));

    return converted;
  }
}
