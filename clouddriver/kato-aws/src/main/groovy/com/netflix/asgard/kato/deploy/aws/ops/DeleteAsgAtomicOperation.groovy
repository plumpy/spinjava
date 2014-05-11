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

package com.netflix.asgard.kato.deploy.aws.ops

import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.netflix.asgard.kato.data.task.Task
import com.netflix.asgard.kato.data.task.TaskRepository
import com.netflix.asgard.kato.deploy.aws.description.DeleteAsgDescription
import com.netflix.asgard.kato.orchestration.AtomicOperation

import static com.netflix.asgard.kato.deploy.aws.StaticAmazonClients.getAutoScaling

class DeleteAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  final DeleteAsgDescription description

  DeleteAsgAtomicOperation(DeleteAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Delete ASG Operation..."
    def autoScaling = getAutoScaling(description.credentials, description.region)

    task.updateStatus BASE_PHASE, "Removing ASG -> ${description.asgName}"
    def request = new DeleteAutoScalingGroupRequest().withAutoScalingGroupName(description.asgName)
      .withForceDelete(description.forceDelete)
    autoScaling.deleteAutoScalingGroup(request)
    task.updateStatus BASE_PHASE, "Deleted ASG -> ${description.asgName}"
    task.updateStatus BASE_PHASE, "Finished Deleting ASG."
  }
}
