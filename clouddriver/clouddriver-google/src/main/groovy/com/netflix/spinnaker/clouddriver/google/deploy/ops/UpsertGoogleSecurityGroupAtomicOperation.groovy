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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

/**
 * Update or insert a firewall rule for the specified network with the project.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/firewalls/update}
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/firewalls/insert}
 */
class UpsertGoogleSecurityGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SECURITY_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertGoogleSecurityGroupDescription description

  UpsertGoogleSecurityGroupAtomicOperation(UpsertGoogleSecurityGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertSecurityGroup": { "securityGroupName": "mysecuritygroup", "network": "default", "credentials": "my-account-name", "sourceRanges":["192.168.0.0/16"], "allowed":[{"ipProtocol":"tcp", "portRanges":["80"]}] }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of security group " +
        "$description.securityGroupName for network $description.network..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def firewallRuleName = description.securityGroupName

    def firewall = GCEUtil.buildFirewallRule(project, description, compute, task, BASE_PHASE)

    try {
      task.updateStatus BASE_PHASE, "Attempting to retrieve existing firewall rule $firewallRuleName for network " +
        "$description.network..."

      def origFirewall = compute.firewalls().get(project, firewallRuleName).execute()

      task.updateStatus BASE_PHASE, "Updating existing firewall rule $firewallRuleName..."

      if (description.targetTags == null) {
        firewall.targetTags = origFirewall.targetTags
      }

      // If the firewall rule already exists, update it.
      compute.firewalls().update(project, firewallRuleName, firewall).execute()
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        // If the firewall rule does not exist, insert a new one.
        task.updateStatus BASE_PHASE, "Inserting new firewall rule $firewallRuleName for network " +
          "$description.network..."

        if (description.targetTags == null) {
          task.updateStatus BASE_PHASE, "Generating target tag for firewall rule $firewallRuleName..."

          firewall.targetTags = ["$firewallRuleName-${System.currentTimeMillis()}".toString()]
        }

        compute.firewalls().insert(project, firewall).execute()
      } else {
        throw e;
      }
    }

    task.updateStatus BASE_PHASE, "Done upserting security group $firewallRuleName for network $description.network."
    null
  }
}
