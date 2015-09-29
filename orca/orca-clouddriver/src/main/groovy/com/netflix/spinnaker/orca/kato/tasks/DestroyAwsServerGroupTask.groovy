/*
 * Copyright 2015 Google, Inc.
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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.orca.kato.pipeline.DestroyAsgStage.DESTROY_ASG_DESCRIPTIONS_KEY

/**
 * TODO: This task can be moved to clouddriver.tasks package once the convert() method has been cleaned up using the new oort APIs
 */
@Component
@CompileStatic
@Deprecated
class DestroyAwsServerGroupTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Override
  TaskResult execute(Stage stage) {
    Map context = convert(stage)
    String cloudProvider = getCloudProvider(stage)
    TaskId taskId = kato.requestOperations(cloudProvider, [[destroyServerGroup: context]])
                     .toBlocking()
                     .first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
        "notification.type"   : "destroyservergroup",
        "deploy.account.name" : context.credentials,
        "kato.last.task.id"   : taskId,
        "asgName"             : context.serverGroupName,  // TODO: Retire asgName
        "serverGroupName"     : context.serverGroupName,
        "deploy.server.groups": ((Iterable) context.regions).collectEntries { [(it): [context.serverGroupName]] }
    ])
  }

  Map convert(Stage stage) {
    def input = stage.context
    // TODO: Remove this if-block
    if (stage.context.containsKey(DESTROY_ASG_DESCRIPTIONS_KEY) &&
        stage.context[DESTROY_ASG_DESCRIPTIONS_KEY]) {
      input = ((List) stage.context[DESTROY_ASG_DESCRIPTIONS_KEY]).pop()
    }

    Map context = mapper.convertValue(input, Map)
    context.serverGroupName = (context.serverGroupName ?: context.asgName) as String

    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      def targetReference = targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage)
      context.asgName = targetReference.asg.name
      context.serverGroupName = targetReference.asg.name
    }

    context
  }
}
