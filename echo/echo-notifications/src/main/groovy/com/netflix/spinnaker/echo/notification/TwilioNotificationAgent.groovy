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

import com.netflix.spinnaker.echo.twilio.TwilioService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Slf4j
@Service
class TwilioNotificationAgent extends AbstractEventNotificationAgent {

    @Autowired
    TwilioService twilioService

    @Value('${twilio.account}')
    String account

    @Value('${twilio.from}')
    String from

    @Override
    void sendNotifications(Map event, Map config, String status) {
        String application = event.details.application

        log.info("Twilio: ${lastCheck}: Event: ${application} ${config.type} ${status} ${event.content?.executionId}")

        mayoService.getNotificationPreferences(application)?.sms?.each { preference ->
            if (preference.when?.contains("$config.type.$status".toString())) {
                try {
                    String name = event.content?.execution?.name ?: event.content?.execution?.description
                    String link = "${spinnakerUrl}/#/applications/${application}/${config.link}/${event.content?.execution?.id}"
                    log.info("Twilio: sms for ${preference.address} - ${link}")

                    String buildInfo = ''

                    if(config.type == 'pipeline'){
                        if(event.content?.execution?.trigger?.buildInfo?.url){
                            buildInfo = """build #${event.content.execution.trigger.buildInfo.number as Integer} """
                        }
                    }

                    twilioService.sendMessage(
                            account,
                            from,
                            preference.address,
                            """The Spinnaker ${config.type} for ${event.content?.execution?.name ?: event.content?.execution?.description} ${buildInfo}${status == 'starting' ? 'is' : 'has'} ${status == 'complete' ? 'completed successfully' : status} for application ${application} ${link}"""
                    )
                } catch (Exception e) {
                    log.error('failed to send sms message ', e)
                }
            }
        }
    }
}
