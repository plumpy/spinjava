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

package com.netflix.spinnaker.orca.kato.tasks.gce

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.ops.gce.ResizeGoogleReplicaPoolOperation
import com.netflix.spinnaker.orca.pipeline.Stage
import org.springframework.beans.factory.annotation.Autowired

class ResizeGoogleReplicaPoolTask implements Task {
  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    def resizeGoogleReplicaPoolOperation = convert(stage)
    def taskId = kato.requestOperations([[resizeGoogleReplicaPoolDescription: resizeGoogleReplicaPoolOperation]])
                     .toBlocking()
                     .first()
    new DefaultTaskResult(PipelineStatus.SUCCEEDED, [
      "notification.type"   : "resizegooglereplicapool",
      "deploy.account.name" : resizeGoogleReplicaPoolOperation.credentials,
      "kato.last.task.id"   : taskId,
      "kato.task.id"        : taskId, // TODO retire this.
      "deploy.server.groups": [(resizeGoogleReplicaPoolOperation.zone): [resizeGoogleReplicaPoolOperation.replicaPoolName]],
    ])
  }

  ResizeGoogleReplicaPoolOperation convert(Stage stage) {
    new ResizeGoogleReplicaPoolOperation(replicaPoolName: stage.context.asgName,
      zone: stage.context.zones ? stage.context.zones[0] : null,
      credentials: stage.context.credentials,
      numReplicas: stage.context.capacity.desired)
  }
}
