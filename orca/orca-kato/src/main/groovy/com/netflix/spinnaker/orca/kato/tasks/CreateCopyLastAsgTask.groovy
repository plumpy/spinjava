/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.ops.AllowLaunchOperation
import com.netflix.spinnaker.orca.pipeline.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

class CreateCopyLastAsgTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Value('${default.bake.account:test}')
  String defaultBakeAccount

  @Override
  TaskResult execute(Stage stage) {
    def operation = mapper.copy()
                          .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
                          .convertValue(stage.context, Map)
    operation.amiName = operation.amiName ?: stage.context.'bake.ami' as String
    operation.remove('type')
    operation.remove('user')
    def taskId = kato.requestOperations(getDescriptions(operation))
                     .toBlocking()
                     .first()

    new DefaultTaskResult(PipelineStatus.SUCCEEDED, [
      "notification.type"  : "createcopylastasg",
      "kato.last.task.id"  : taskId,
      "kato.task.id"       : taskId, // TODO retire this.
      "deploy.account.name": operation.credentials,
    ])
  }

  private List<Map<String, Object>> getDescriptions(Map operation) {
    List<Map<String, Object>> descriptions = []
    if (operation.credentials != defaultBakeAccount) {
      def allowLaunchDescriptions = operation.availabilityZones.collect { String region, List<String> azs ->
        [
          allowLaunchDescription: new AllowLaunchOperation(
            account: operation.credentials,
            credentials: defaultBakeAccount,
            region: region,
            amiName: operation.amiName
          )
        ]
      }
      descriptions.addAll(allowLaunchDescriptions)
    }
    descriptions.add([copyLastAsgDescription: operation])
    descriptions
  }
}
