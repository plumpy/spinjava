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

package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.pipeline.model.ImmutableStage
import org.springframework.beans.factory.annotation.Autowired
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

class UpdateApplicationTask implements Task {

  @Autowired
  Front50Service front50Service

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(ImmutableStage stage) {
    def application = mapper.copy()
                            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .convertValue(stage.context.application, Application)

    def resp = front50Service.update(stage.context.account as String, application)
    if (resp.status != 200) {
      new DefaultTaskResult(ExecutionStatus.TERMINAL)
    } else {
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
        "application.name": application.name,
        "account"         : stage.context.account
      ])
    }
  }
}
