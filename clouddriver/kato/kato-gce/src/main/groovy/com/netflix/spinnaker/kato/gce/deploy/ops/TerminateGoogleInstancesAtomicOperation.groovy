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

import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.InstanceGroupManagersRecreateInstancesRequest
import com.netflix.spinnaker.clouddriver.google.util.ReplicaPoolBuilder
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.TerminateGoogleInstancesDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

/**
 * Terminate and delete instances. If the instances are in a managed instance group they will be recreated.
 *
 * If no managed instance group is specified, this operation only explicitly deletes and removes the instances. However,
 * if the instances are in a managed instance group then the manager will automatically recreate and restart the
 * instances once it sees that they are missing. The net effect is to recreate the instances. More information:
 * {@link https://cloud.google.com/compute/docs/instances#deleting_an_instance}
 *
 * If a managed instance group is specified, this becomes a first-class explicit operation on the managed instance
 * group to terminate and recreate the instances. More information:
 * {@link https://cloud.google.com/compute/docs/instance-groups/manager/v1beta2/instanceGroupManagers/recreateInstances}
 */
class TerminateGoogleInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final TerminateGoogleInstancesDescription description
  private final ReplicaPoolBuilder replicaPoolBuilder

  TerminateGoogleInstancesAtomicOperation(TerminateGoogleInstancesDescription description,
                                          ReplicaPoolBuilder replicaPoolBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
  }

  /**
   * Attempt to terminate each of the specified instances.
   *
   * If no managed instance group is specified, this will attempt to terminate each of the instances independent of one
   * another. Should any of them throw an exception, the first one will be propagated from this method, but the other
   * attempts will be allowed to complete first. Currently, if others also throw an exception then those exceptions will
   * be lost (however, their failures will be logged in the status).
   *
   * If a managed instance group is specified, we rely on the manager to terminate and recreate the instances.
   *
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstances": { "instanceIds": ["myapp-dev-v000-abcd"], "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstances": { "managedInstanceGroupName": "myapp-dev-v000", "instanceIds": ["myapp-dev-v000-abcd"], "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing termination of instances [${description.instanceIds.join(", ")}]."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def instanceIds = description.instanceIds

    if (description.managedInstanceGroupName) {
      task.updateStatus BASE_PHASE, "Recreating instances [${instanceIds.join(", ")}] in managed instance " +
          "group $description.managedInstanceGroupName."

      def managerName = description.managedInstanceGroupName
      def credentialBuilder = description.credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
      def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder)
      def instanceGroupManagers = replicapool.instanceGroupManagers()

      def request = new InstanceGroupManagersRecreateInstancesRequest().setInstances(instanceIds)
      instanceGroupManagers.recreateInstances(project, zone, managerName, request).execute()

      task.updateStatus BASE_PHASE, "Done executing recreate of instances [${instanceIds.join(", ")}]."
    } else {
      def firstFailure
      def okIds = []
      def failedIds = []

      for (def instanceId : instanceIds) {
        task.updateStatus BASE_PHASE, "Terminating instance $instanceId..."

        try {
          compute.instances().delete(project, zone, instanceId).execute()
          okIds.add(instanceId)
        } catch (Exception e) {
          task.updateStatus BASE_PHASE, "Failed to terminate instance $instanceId in zone $zone: $e.message."
          failedIds.add(instanceId)

          if (!firstFailure) {
            firstFailure = e
          }
        }
      }

      if (firstFailure) {
        task.updateStatus BASE_PHASE, "Failed to terminate instances [${failedIds.join(", ")}] but successfully " +
            "terminated instances [${okIds.join(", ")}]."
        throw firstFailure
      }

      task.updateStatus BASE_PHASE, "Successfully terminated all instances [${instanceIds.join(", ")}]."
    }

    null
  }
}
