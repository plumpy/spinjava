/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.events.EchoEventListener
import com.netflix.spinnaker.echo.services.Front50Service
import com.netflix.spinnaker.echo.model.Event
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

abstract class AbstractEventNotificationAgent implements EchoEventListener {

    @Autowired
    Front50Service front50Service

    @Value('${spinnaker.baseUrl}')
    String spinnakerUrl

    static Map CONFIG = [
        'pipeline': [
            type: 'pipeline',
            link: 'executions'
        ],
        'task'    : [
            type: 'task',
            link: 'tasks'
        ],
        'stage'   : [
            type: 'stage',
            link: 'stage'
        ]
    ]

    @Override
    void processEvent(Event event) {

        if (event.details.type.startsWith("orca:")) {

            List eventDetails = event.details.type.split(':')

            Map<String, String> config = CONFIG[eventDetails[1]]
            String status = eventDetails[2]

            if (!config || !config.type) {
                return
            }

            if (config.type == 'task' && event.content.standalone == false) {
                return
            }

            if (config.type == 'task' && event.content.canceled == true) {
                return
            }

            if (config.type == 'pipeline' && event.content.execution?.canceled == true) {
                return
            }

            // send application level notification

            String application = event.details.application

            def sendRequests = []

            // application level
            def preferenceList = front50Service.getNotificationPreferences(application)
            def preferences = []

            if (preferenceList) {
                preferences = preferenceList[getNotificationType()]
            }

            preferences.each { preference ->
                if (preference.when?.contains("$config.type.$status".toString())) {
                    sendRequests << preference
                }
            }

            // pipeline level

            if (config.type == 'pipeline') {
                event.content?.execution.notifications?.each { notification ->
                    String key = "${getNotificationType()}"
                    if (notification[key]?.when?.contains("$config.type.$status".toString())) {
                        sendRequests << notification[key]
                    }
                }
            }

            if (config.type == 'stage') {
                event.content?.context?.notifications?.each { notification ->
                    String key = "${getNotificationType()}"
                    if (notification[key]?.when?.contains("$config.type.$status".toString())) {
                        sendRequests << notification[key]
                    }
                }
            }

            sendRequests.each{
                sendNotifications(it, application, event, config, status)
            }

        }
    }

    abstract String getNotificationType()

    abstract void sendNotifications(Map preference, String application, Event event, Map config, String status)

}
