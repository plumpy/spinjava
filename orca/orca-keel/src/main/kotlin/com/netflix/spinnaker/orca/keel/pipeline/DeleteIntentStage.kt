/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.keel.pipeline

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.keel.task.DeleteIntentTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class DeleteIntentStage() : StageDefinitionBuilder {
  override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
    builder.withTask<DeleteIntentTask>("deleteIntent")
  }
}

inline fun <reified T : Task> TaskNode.Builder.withTask(name: String) =
  withTask(name, T::class.java)
