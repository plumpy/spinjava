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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.ImmutableStage
import org.springframework.beans.factory.annotation.Autowired

class NotifyEchoTask implements Task {

  @Autowired(required=false)
  EchoService echo

  @Override
  TaskResult execute(ImmutableStage stage) {

    if (echo) {
      def inputs = stage.context
      echo.recordEvent(
        [
          "details": [
            "source"     : "kato",
            "type"       : inputs."notification.type",
            "application": inputs.application
          ],
          "content": inputs
        ]
      )
    }

    new DefaultTaskResult(PipelineStatus.SUCCEEDED)

  }

}
