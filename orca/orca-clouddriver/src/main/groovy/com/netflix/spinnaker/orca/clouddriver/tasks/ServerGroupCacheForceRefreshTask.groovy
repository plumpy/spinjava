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

package com.netflix.spinnaker.orca.clouddriver.tasks
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class ServerGroupCacheForceRefreshTask extends AbstractCloudProviderAwareTask implements Task {

  static final String REFRESH_TYPE = "ServerGroup"

  @Autowired
  OortService oort

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)
    Map<String, List<String>> capturedServerGroups = (Map<String, List<String>>) stage.context."deploy.server.groups"

    def zone = stage.context.zone
    if (!zone) {
      zone = stage.context.zones ? stage.context.zones[0] : null
    }

    def outputs = [:]
    capturedServerGroups?.each { region, serverGroups ->
      for (serverGroup in serverGroups) {
        def model = [serverGroupName: serverGroup, asgName: serverGroup, region: region, account: account] // TODO retire asgName
        if (zone) {
          model.zone = zone
        }
        try {
          oort.forceCacheUpdate(cloudProvider, REFRESH_TYPE, model)
        } catch (e) {
          if (!outputs.containsKey("force.cache.refresh.errors")) {
            outputs["force.cache.refresh.errors"] = []
          }
          outputs["force.cache.refresh.errors"] << e.message
        }
      }
    }
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }
}
