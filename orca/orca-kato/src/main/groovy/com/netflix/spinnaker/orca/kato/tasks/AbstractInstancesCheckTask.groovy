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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractInstancesCheckTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = 600000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  abstract protected Map<String, List<String>> getServerGroups(TaskContext context)

  abstract protected boolean hasSucceeded(List instances)

  @Override
  TaskResult execute(TaskContext context) {
    String account = context.getInputs()."deploy.account.name"

    Map<String, List<String>> serverGroups = getServerGroups(context)

    if (!serverGroups || !serverGroups?.values()?.flatten()) {
      return new DefaultTaskResult(TaskResult.Status.FAILED)
    }
    Names names = Names.parseName(serverGroups.values().flatten()[0])
    def response = oortService.getCluster(names.app, account, names.cluster)
    if (response.status != 200) {
      return new DefaultTaskResult(TaskResult.Status.RUNNING)
    }
    def cluster = objectMapper.readValue(response.body.in().text, Map)
    if (!cluster || !cluster.serverGroups) {
      return new DefaultTaskResult(TaskResult.Status.RUNNING)
    }
    Map<String, Boolean> seenServerGroup = serverGroups.values().flatten().collectEntries { [(it): false] }
    for (Map serverGroup in cluster.serverGroups) {
      String region = serverGroup.region
      String name = serverGroup.name

      List instances = serverGroup.instances
      Map asg = serverGroup.asg
      int minSize = asg.minSize

      if (!serverGroups.containsKey(region) || !serverGroups[region].contains(name) || minSize > instances.size()) {
        continue
      }

      seenServerGroup[name] = true
      def isComplete = hasSucceeded(instances)
      if (!isComplete) {
        return new DefaultTaskResult(TaskResult.Status.RUNNING)
      }
    }
    if (seenServerGroup.values().contains(false)) {
      new DefaultTaskResult(TaskResult.Status.RUNNING)
    } else {
      new DefaultTaskResult(TaskResult.Status.SUCCEEDED)
    }
  }

}
