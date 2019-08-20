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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.manifest;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.UNDO_ROLLOUT_MANIFEST;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.converters.KubernetesAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesUndoRolloutManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesUndoRolloutManifestOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import java.util.Map;
import org.springframework.stereotype.Component;

@KubernetesOperation(UNDO_ROLLOUT_MANIFEST)
@Component
public class KubernetesUndoRolloutManifestConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new KubernetesUndoRolloutManifestOperation(convertDescription(input));
  }

  @Override
  public KubernetesUndoRolloutManifestDescription convertDescription(Map input) {
    return KubernetesAtomicOperationConverterHelper.convertDescription(
        input, this, KubernetesUndoRolloutManifestDescription.class);
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v2;
  }
}
