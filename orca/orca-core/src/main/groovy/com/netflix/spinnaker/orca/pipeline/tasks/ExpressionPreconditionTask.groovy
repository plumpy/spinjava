/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.orca.pipeline.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import org.springframework.stereotype.Component

@Component
class ExpressionPreconditionTask implements PreconditionTask {
  final String preconditionType = 'expression'

  @Override
  TaskResult execute(Stage stage) {
    def stageData = stage.mapTo("/context", StageData)

    def augmentedContext = [:] + stage.context
    if (stage.execution instanceof Pipeline) {
      augmentedContext.put('trigger', ((Pipeline) stage.execution).trigger)
      augmentedContext.put('execution', stage.execution)
    }

    String expression = ContextParameterProcessor.process([
        "expression": '${' + stageData.expression + '}'
    ], augmentedContext, true).expression

    def matcher = expression =~ /\$\{(.*)\}/
    if (matcher.matches()) {
      expression = matcher.group(1)
    }

    def status = Boolean.valueOf(expression) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL
    return new DefaultTaskResult(status, [
      context: new HashMap(stage.context.context as Map) + [
          expressionResult: expression
      ]
    ])
  }

  static class StageData {
    String expression = "false"
  }
}
