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

import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.TargetPool
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

class DeleteGoogleNetworkLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_NETWORK_LOAD_BALANCER"
  // The resources will probably get deleted but we should verify that the operation succeeded. 15 seconds was the
  // minimum duration it took the operation to finish while writing this code.
  private static final int DEFAULT_ASYNC_OPERATION_TIMEOUT_SEC = 15

  static class HealthCheckAsyncDeleteOperation {
    String healthCheckName
    String operationName
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private static handleFinishedAsyncDeleteOperation(Operation operation, String resourceType, String resourceName) {
    if (operation == null) {
      GCEUtil.updateStatusAndThrowException("Delete operation of $resourceType $resourceName timed out. The resource " +
          "may still exist.", task, BASE_PHASE)
    }
    if (operation.getError() != null) {
      def error = operation.getError().getErrors().get(0)
      GCEUtil.updateStatusAndThrowException("Failed to delete $resourceType $resourceName with error: $error", task,
          BASE_PHASE)
    }
    task.updateStatus BASE_PHASE, "Done deleting $resourceType $resourceName."
  }

  private final DeleteGoogleNetworkLoadBalancerDescription description

  DeleteGoogleNetworkLoadBalancerAtomicOperation(DeleteGoogleNetworkLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteGoogleNetworkLoadBalancerDescription": { "networkLoadBalancerName": "myapp-dev-v000", "zone": "us-central1-b", "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of network load balancer $description.networkLoadBalancerName " +
        "in $description.zone..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def region = GCEUtil.getRegionFromZone(project, zone, compute)
    def forwardingRuleName = description.networkLoadBalancerName

    task.updateStatus BASE_PHASE, "Retrieving forwarding rule $forwardingRuleName in $region..."

    ForwardingRule forwardingRule =
        compute.forwardingRules().get(project, region, forwardingRuleName).execute()
    if (forwardingRule == null) {
      GCEUtil.updateStatusAndThrowException("Forwarding rule $forwardingRuleName not found in $region for $project",
          task, BASE_PHASE)
    }
    def targetPoolName = GCEUtil.getLocalName(forwardingRule.getTarget())

    task.updateStatus BASE_PHASE, "Retrieving target pool $targetPoolName in $region..."

    TargetPool targetPool = compute.targetPools().get(project, region, targetPoolName).execute()
    if (targetPool == null) {
      GCEUtil.updateStatusAndThrowException("Target pool $targetPoolName not found in $region for $project",
          task, BASE_PHASE)
    }
    def healthCheckUrls = targetPool.getHealthChecks()

    task.updateStatus BASE_PHASE, "Deleting forwarding rule $forwardingRuleName in $region..."
    Operation deleteForwardingRuleOperation =
        compute.forwardingRules().delete(project, region, forwardingRuleName).execute()

    task.updateStatus BASE_PHASE, "Deleting target pool $targetPoolName in $region..."
    Operation deleteTargetPoolOperation =
        compute.targetPools().delete(project, region, targetPoolName).execute()

    List<HealthCheckAsyncDeleteOperation> deleteHealthCheckAsyncOperations =
        new ArrayList<HealthCheckAsyncDeleteOperation>()
    for (String healthCheckUrl : healthCheckUrls) {
      def healthCheckName = GCEUtil.getLocalName(healthCheckUrl)
      task.updateStatus BASE_PHASE, "Deleting health check $healthCheckName for $project..."
      Operation deleteHealthCheckOp = compute.httpHealthChecks().delete(project, healthCheckName).execute()
      deleteHealthCheckAsyncOperations.add(new HealthCheckAsyncDeleteOperation(
          healthCheckName: healthCheckName,
          operationName: deleteHealthCheckOp.getName()))
    }

    def timeoutSeconds = description.deleteOperationTimeoutSeconds != null ?
        description.deleteOperationTimeoutSeconds : DEFAULT_ASYNC_OPERATION_TIMEOUT_SEC
    def deadline = System.currentTimeMillis() + timeoutSeconds * 1000
    // TODO(odedmeri): Merge handleFinishedAsyncDeleteOperation and waitFor{Global,Regional}Operation into one function.
    handleFinishedAsyncDeleteOperation(
        GCEUtil.waitForRegionalOperation(compute, project, region, deleteForwardingRuleOperation.getName(),
            Math.max(deadline - System.currentTimeMillis(), 0)),
        "forwarding rule", forwardingRuleName)
    handleFinishedAsyncDeleteOperation(
        GCEUtil.waitForRegionalOperation(compute, project, region, deleteTargetPoolOperation.getName(),
            Math.max(deadline - System.currentTimeMillis(), 0)),
        "target pool", targetPoolName)

    for (HealthCheckAsyncDeleteOperation asyncOperation : deleteHealthCheckAsyncOperations) {
      handleFinishedAsyncDeleteOperation(
          GCEUtil.waitForGlobalOperation(compute, project, asyncOperation.operationName,
              Math.max(deadline - System.currentTimeMillis(), 0)),
          "health check", asyncOperation.healthCheckName)
    }

    task.updateStatus BASE_PHASE, "Done deleting network load balancer $description.networkLoadBalancerName."
    null
  }


}
