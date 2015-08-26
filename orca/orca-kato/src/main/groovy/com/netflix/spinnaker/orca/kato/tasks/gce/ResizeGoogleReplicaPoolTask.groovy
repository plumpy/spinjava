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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class ResizeGoogleReplicaPoolTask implements Task {
  @Autowired
  KatoService kato

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  ResizeSupport resizeSupport

  @Override
  TaskResult execute(Stage stage) {
    def resizeGoogleReplicaPoolOperation = convert(stage)
    def taskId = kato.requestOperations([[resizeGoogleReplicaPoolDescription: resizeGoogleReplicaPoolOperation]])
                     .toBlocking()
                     .first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"   : "resizegooglereplicapool",
      "deploy.account.name" : resizeGoogleReplicaPoolOperation.credentials,
      "kato.last.task.id"   : taskId,
      "kato.task.id"        : taskId, // TODO retire this.
      "deploy.server.groups": [(resizeGoogleReplicaPoolOperation.region): [resizeGoogleReplicaPoolOperation.replicaPoolName]],
      "asgName"             : resizeGoogleReplicaPoolOperation.replicaPoolName,
      "numReplicas"         : resizeGoogleReplicaPoolOperation.numReplicas,
    ])
  }

  Map convert(Stage stage) {
    def operation = [:]
    operation.putAll(stage.context)

    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      def targetRefs = targetReferenceSupport.getTargetAsgReferences(stage)
      def descriptors = resizeSupport.createResizeStageDescriptors(stage, targetRefs)
      if (descriptors && !descriptors.isEmpty()) {
        operation.putAll(descriptors[0])
      }
    }

    operation.replicaPoolName = operation.asgName
    operation.numReplicas = operation.capacity.desired
    operation.region = operation.regions ? operation.regions[0] : null
    operation.zone = operation.zones ? operation.zones[0] : null
    operation
  }
}
