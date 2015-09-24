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

import com.google.api.services.compute.model.InstanceGroupManagersDeleteInstancesRequest
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.TerminateAndDecrementGoogleServerGroupDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

/**
 * Terminate and delete instances from a replica pool, and decrement the size of the replica pool.
 *
 * This operation explicitly deletes and removes specific instances from
 * managed instance group, decreasing the size of the group by the number of
 * instances removed. The basic {@link TerminateGoogleInstancesAtomicOperation}
 * will delete the instances as this does, however since the managed instance
 * group was not changed, it will create new instances to satisfy its size
 * requirements.
 *
 * This is an alternative to {@link TerminateGoogleInstancesAtomicOperation} using
 * the API described in {@link https://cloud.google.com/compute/docs/instance-groups/manager/v1beta2/instanceGroupManagers/deleteInstances}.
 *
 * This is also an alternative to {@link AbandonAndDecrementGoogleServerGroupAtomicOperation}
 * where the instances are also deleted once removed from the replica pool.
 *
 * @see TerminateGoogleInstancesAtomicOperation
 */
class TerminateAndDecrementGoogleServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final TerminateAndDecrementGoogleServerGroupDescription description

  TerminateAndDecrementGoogleServerGroupAtomicOperation(TerminateAndDecrementGoogleServerGroupDescription description) {
    this.description = description
  }

  /**
   * Attempt to terminate the specified instanceIds and remove from the specified replica pool.
   *
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateAndDecrementGoogleServerGroupDescription": { "replicaPoolName": "myapp-dev-v000", "instanceIds": ["myapp-dev-v000-abcd"], "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Attempting to terminate instances (${description.instanceIds.join(", ")} and remove from replica pool (${description.replicaPoolName})."

    def project = description.credentials.project
    def compute = description.credentials.compute
    def zone = description.zone
    def replicaPoolName = description.replicaPoolName
    def instanceIds = description.instanceIds
    def instanceUrls = GCEUtil.deriveInstanceUrls(project, zone, replicaPoolName, instanceIds, description.credentials)
    def deleteRequest = new InstanceGroupManagersDeleteInstancesRequest().setInstances(instanceUrls)

    compute.instanceGroupManagers().deleteInstances(project, zone, replicaPoolName, deleteRequest).execute()
    task.updateStatus BASE_PHASE, "Successfully terminated instances=(${description.instanceIds.join(", ")} and removed from ${replicaPoolName})."
    null
  }
}
