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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.echo.EchoService
import org.springframework.beans.factory.annotation.Autowired

class NotifyEchoTask implements Task {

  @Autowired
  EchoService echo

  @Override
  TaskResult execute(TaskContext context) {

    echo.recordEvent(
      [
        "details": [
          "source": "kato",
          "type"  : context.inputs."notification.type",
          "application" : context.inputs.application
        ],
        "content": context.inputs
      ]
    )

    new DefaultTaskResult(TaskResult.Status.SUCCEEDED)

  }

}
