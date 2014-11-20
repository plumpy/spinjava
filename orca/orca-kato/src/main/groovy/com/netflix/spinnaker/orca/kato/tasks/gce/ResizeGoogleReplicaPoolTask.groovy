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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.ops.gce.ResizeGoogleReplicaPoolOperation
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

class ResizeGoogleReplicaPoolTask implements Task {
  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

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
    def operation = [:]
    operation.putAll(stage.context)
    operation.replicaPoolName = operation.asgName
    operation.numReplicas = operation.capacity.desired
    operation.zone = operation.zones ? operation.zones[0] : null

    mapper.copy()
          .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
          .convertValue(operation, ResizeGoogleReplicaPoolOperation)
  }
}
