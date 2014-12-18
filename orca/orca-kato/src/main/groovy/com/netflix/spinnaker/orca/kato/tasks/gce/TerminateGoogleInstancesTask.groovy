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

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class TerminateGoogleInstancesTask implements Task {
  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    def taskId = kato.requestOperations([[terminateGoogleInstancesDescription: stage.context]])
                     .toBlocking()
                     .first()

    // TODO(duftler): Reconcile the mismatch between region and zone here.
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"     : "terminategoogleinstances",
      "terminate.account.name": stage.context.credentials,
      "terminate.region"      : stage.context.zone,
      "kato.last.task.id"     : taskId,
      "kato.task.id"          : taskId, // TODO retire this.
      "terminate.instance.ids": stage.context.instanceIds,
    ])
  }
}
