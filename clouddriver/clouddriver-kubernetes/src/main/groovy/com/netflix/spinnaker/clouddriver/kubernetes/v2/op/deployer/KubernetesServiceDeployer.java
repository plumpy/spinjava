/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.models.V1Service;
import org.springframework.stereotype.Component;

@Component
public class KubernetesServiceDeployer extends KubernetesDeployer<V1Service> {
  @Override
  Class<V1Service> getDeployedClass() {
    return V1Service.class;
  }

  @Override
  void deploy(KubernetesV2Credentials credentials, V1Service resource) {
    credentials.deployService(resource);
  }
}
