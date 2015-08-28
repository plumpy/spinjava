/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kato.titan.deploy.ops
import com.netflix.spinnaker.clouddriver.titan.TitanClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.titan.deploy.description.ResizeTitanServerGroupDescription
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.model.Job
import groovy.util.logging.Slf4j
/**
 * @author sthadeshwar
 */
@Slf4j
class ResizeTitanServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "RESIZE_TITAN_SERVER_GROUP"
  private final TitanClientProvider titanClientProvider
  private final ResizeTitanServerGroupDescription description

  ResizeTitanServerGroupAtomicOperation(TitanClientProvider titanClientProvider,
                                        ResizeTitanServerGroupDescription description) {
    this.titanClientProvider = titanClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    List<ResizeTitanServerGroupDescription.TitanServerGroupDescription> serverGroups = description.serverGroupDescriptions
    task.updateStatus PHASE, "Starting resize server groups operation for ${serverGroups.collect { it.serverGroupName }}"

    for (serverGroup in serverGroups) {
      task.updateStatus PHASE, "Resizing server group: ${serverGroup.serverGroupName} to ${serverGroup.capacity}..."
      TitanClient titanClient = titanClientProvider.getTitanClient(description.credentials, serverGroup.region)
      Job job = titanClient.findJobByName(serverGroup.serverGroupName)
      Map jobAttributes = [instances: serverGroup.capacity.desired]
      titanClient.updateJob(job.id, jobAttributes)
      task.updateStatus PHASE, "Issued resize request to titan for job ${serverGroup.serverGroupName}"
      task.updateStatus PHASE, "Completed resize server group operation for ${serverGroup.serverGroupName}"
    }

    task.updateStatus PHASE, "Completed resize server groups operation for ${serverGroups.collect { it.serverGroupName }}"
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
