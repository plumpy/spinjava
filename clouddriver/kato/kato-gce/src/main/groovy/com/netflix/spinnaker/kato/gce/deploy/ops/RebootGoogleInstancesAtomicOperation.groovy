/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.RebootGoogleInstancesDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

/**
 * Resets each specified instance back to the initial state of its underlying virtual machine.
 *
 * @see https://cloud.google.com/compute/docs/instances/restarting-an-instance
 */
class RebootGoogleInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESET_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final RebootGoogleInstancesDescription description

  RebootGoogleInstancesAtomicOperation(RebootGoogleInstancesDescription description) {
    this.description = description
  }

  /**
   * Attempt to reset each of the specified instanceIds independent of one another.
   *
   * Throws a runtime exception if any of the resets fails. The exception only contains the
   * first error. Additional errors from other instances are only visible in the task status.
   *
   * curl -X POST -H "Content-Type: application/json" -d '[ { "rebootGoogleInstancesDescription": { "instanceIds": ["myapp-dev-v000-abcd"], "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing reset of instances (${description.instanceIds.join(", ")}) in " +
      "$description.zone..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def firstFailure
    def okIds = []
    def failedIds = []

    for (def instanceId : description.instanceIds) {
      task.updateStatus BASE_PHASE, "Attempting to reset instance $instanceId in $zone..."

      try {
        compute.instances().reset(project, zone, instanceId).execute()
        okIds.add(instanceId)
      } catch (Exception e) {
        task.updateStatus BASE_PHASE, "Failed to reset instance $instanceId in $zone: $e.message."
        failedIds.add(instanceId)
        if (!firstFailure) {
          firstFailure = e
        }
      }
    }

    if (firstFailure) {
      task.updateStatus BASE_PHASE, "Failed to reset instances (${failedIds.join(", ")}), but sucessfully reset " +
        "instances (${okIds.join(", ")})."
      throw firstFailure
    }

    task.updateStatus BASE_PHASE, "Done resetting instances (${description.instanceIds.join(", ")}) in $zone."
    null
  }
}
