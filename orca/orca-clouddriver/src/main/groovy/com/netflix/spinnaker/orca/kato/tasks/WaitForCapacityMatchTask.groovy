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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class WaitForCapacityMatchTask extends AbstractInstancesCheckTask {
  @Override
  protected Map<String, List<String>> getServerGroups(Stage stage) {
    (Map<String, List<String>>) stage.context."deploy.server.groups"
  }

  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    if (!serverGroup.capacity || serverGroup.capacity.desired != instances.size()) {
      return false
    }
    return !serverGroup.disabled ?
      WaitForUpInstancesTask.allInstancesMatch(stage, serverGroup, instances, interestingHealthProviderNames) :
      true
  }
}
