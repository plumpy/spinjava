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


package com.netflix.spinnaker.orca.notifications

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class BuildJobPollingNotificationAgent extends AbstractPollingNotificationAgent {

  static final String NOTIFICATION_TYPE = "build"
  long pollingInterval = 30
  String notificationType = NOTIFICATION_TYPE

  @Autowired
  BuildJobPollingNotificationAgent(List<NotificationHandler> notificationHandlers) {
    super(notificationHandlers)
  }

  @Override
  void handleNotification(List<Map> resp) {
    for (event in resp) {
      if (event.content.containsKey("project") && event.content.containsKey("master")) {
        Map input = event.content.project as Map
        input.master = event.content.master
        notify(input)
      }
    }
  }
}
