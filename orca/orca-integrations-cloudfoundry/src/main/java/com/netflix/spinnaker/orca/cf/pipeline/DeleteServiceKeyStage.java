/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.cf.pipeline;

import com.netflix.spinnaker.orca.cf.tasks.CloudFoundryDeleteServiceKeyTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.CloudFoundryMonitorKatoServicesTask;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
class DeleteServiceKeyStage implements StageDefinitionBuilder, CloudProviderAware {
  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder
      .withTask("deleteServiceKey", CloudFoundryDeleteServiceKeyTask.class)
      .withTask("monitorDeleteServiceKey", CloudFoundryMonitorKatoServicesTask.class);
  }
}
