/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.kato.tasks

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.DeployOperation
import com.netflix.spinnaker.orca.kato.api.KatoService
import org.springframework.beans.factory.annotation.Autowired
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

@CompileStatic
class CreateDeployTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(TaskContext context) {
    def taskId = kato.requestOperations([[
        basicAmazonDeployDescription: deployOperationFromContext(context)
    ]]).toBlockingObservable().first()

    new DefaultTaskResult(TaskResult.Status.SUCCEEDED, ["deploy.task.id": taskId])
  }

  private DeployOperation deployOperationFromContext(TaskContext context) {
    def operation = mapper.copy()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .convertValue(context.getInputs("deploy"), DeployOperation)
    if (context.inputs."bake.ami") {
      operation.amiName = context.inputs."bake.ami"
    }
    return operation
  }
}
