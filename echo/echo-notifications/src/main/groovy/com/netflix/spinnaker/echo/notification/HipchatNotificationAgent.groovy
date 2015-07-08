/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.hipchat.HipchatMessage
import com.netflix.spinnaker.echo.hipchat.HipchatService
import groovy.util.logging.Slf4j
import org.apache.commons.lang.WordUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Slf4j
@Service
class HipchatNotificationAgent extends AbstractEventNotificationAgent {

    @Autowired
    HipchatService hipchatService

    @Value('${hipchat.token}')
    String token

    @Override
    void sendNotifications(Map event, Map config, String status) {
        String application = event.details.application

        log.info("Hipchat: ${lastCheck}: Event: ${application} ${config.type} ${status} ${event.content?.executionId}")

        mayoService.getNotificationPreferences(application)?.hipchat?.each { preference ->
            if (preference.when?.contains("$config.type.$status".toString())) {
                try {
                    boolean notify = false
                    if(status == 'failed'){
                        notify = true
                    }

                    String color = 'gray'

                    if(status == 'failed'){
                        color = 'red'
                    }

                    if(status == 'complete'){
                        color = 'green'
                    }

                    String buildInfo = ''

                    if(config.type == 'pipeline'){
                        if(event.content?.execution?.trigger?.buildInfo?.url){
                            buildInfo = """build #<a href="${event.content.execution.trigger.buildInfo.url}">${event.content.execution.trigger.buildInfo.number as Integer}</a> """
                        }
                    }

                    hipchatService.sendMessage(
                            token,
                            preference.address,
                            new HipchatMessage(
                                    message: """${WordUtils.capitalize(application)}'s <a href="${spinnakerUrl}/#/applications/${application}/${
                                        config.link
                                    }/${event.content?.execution?.id}">${event.content?.execution?.name ?: event.content?.execution?.description}</a> ${buildInfo} ${config.type} ${status == 'starting' ? 'is' : 'has' } ${status == 'complete' ? 'completed successfully' : status}""",
                                    color: color,
                                    notify: notify
                            )
                    )
                } catch (Exception e) {
                    log.error('failed to send hipchat message ', e)
                }
            }
        }
    }
}

