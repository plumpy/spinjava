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
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleFirewallRuleDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

/**
 * Delete a firewall rule from the specified project.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/firewalls/delete}
 */
class DeleteGoogleFirewallRuleAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_FIREWALL_RULE"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeleteGoogleFirewallRuleDescription description

  DeleteGoogleFirewallRuleAtomicOperation(DeleteGoogleFirewallRuleDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteSecurityGroup": { "cloudProvider": "gce", "firewallRuleName":"mysecuritygroup", "credentials": "my-account-name" }} ]' localhost:7002/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Delete Google Firewall Rule operation for firewall rule " +
        "$description.firewallRuleName..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def firewallRuleName = description.firewallRuleName

    compute.firewalls().delete(project, firewallRuleName).execute()

    task.updateStatus BASE_PHASE, "Done deleting firewall rule $firewallRuleName."
    null
  }
}
