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

package com.netflix.spinnaker.orca.notifications.manual

import groovy.transform.InheritConstructors
import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler
import com.netflix.spinnaker.orca.notifications.PipelineIndexer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component
@Scope(SCOPE_PROTOTYPE)
@InheritConstructors
class ManualTriggerNotificationHandler extends AbstractNotificationHandler {

  String handlerType = ManualTriggerPollingNotificationAgent.NOTIFICATION_TYPE

  @Autowired PipelineIndexer pipelineIndexer

  @Override
  void handle(Map input) {
    def id = new PipelineId(input.application as String, input.name as String)
    def pipelines = pipelineIndexer.pipelines
    if (pipelines.containsKey(id)) {
      def config = new HashMap(pipelines[id][0])
      config.trigger = [type: "manual", user: input.user]
      def json = objectMapper.writeValueAsString(config)
      pipelineStarter.start(json)
    }
  }
}
