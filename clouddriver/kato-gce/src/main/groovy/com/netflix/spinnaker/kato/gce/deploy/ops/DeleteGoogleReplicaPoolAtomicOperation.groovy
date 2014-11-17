/*
 * Copyright 2014 Google, Inc.
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
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleReplicaPoolDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

class DeleteGoogleReplicaPoolAtomicOperation implements AtomicOperation<Void> {
  // TODO(duftler): This should move to a common location.
  private static final String APPLICATION_NAME = "Spinnaker"
  private static final String BASE_PHASE = "DELETE_REPLICA_POOL"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeleteGoogleReplicaPoolDescription description
  private final ReplicaPoolBuilder replicaPoolBuilder

  DeleteGoogleReplicaPoolAtomicOperation(DeleteGoogleReplicaPoolDescription description,
                                         ReplicaPoolBuilder replicaPoolBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of replica pool $description.replicaPoolName in $description.zone..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def replicaPoolName = description.replicaPoolName

    def credentialBuilder = description.credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, APPLICATION_NAME);

    def instanceGroupManager = replicapool.instanceGroupManagers().get(project, zone, replicaPoolName).execute()

    // We create a new instance template for each managed instance group. We need to delete it here.
    def instanceTemplateName = getLocalName(instanceGroupManager.instanceTemplate)

    task.updateStatus BASE_PHASE, "Identified instance template."

    replicapool.instanceGroupManagers().delete(project, zone, replicaPoolName).execute()

    task.updateStatus BASE_PHASE, "Deleted instance group."

    compute.instanceTemplates().delete(project, instanceTemplateName).execute()

    task.updateStatus BASE_PHASE, "Deleted instance template."

    task.updateStatus BASE_PHASE, "Done deleting replica pool $replicaPoolName in $zone."
    null
  }

  private static String getLocalName(String fullUrl) {
    int lastIndex = fullUrl.lastIndexOf('/')

    return lastIndex != -1 ? fullUrl.substring(lastIndex + 1) : fullUrl
  }
}
