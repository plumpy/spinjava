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
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.slack.SlackMessage
import com.netflix.spinnaker.echo.slack.SlackService
import groovy.util.logging.Slf4j
import org.apache.commons.lang.WordUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import static net.logstash.logback.argument.StructuredArguments.*


@Slf4j
@ConditionalOnProperty('slack.enabled')
@Service
class SlackNotificationAgent extends AbstractEventNotificationAgent {

  @Autowired
  SlackService slackService

  @Value('${slack.token}')
  String token

  @Override
  void sendNotifications(Map preference, String application, Event event, Map config, String status) {
    try {
      String buildInfo = ' '

      String color = '#CCCCCC'

      if (status == 'failed') {
        color = '#B82525'
      }

      if (status == 'starting') {
        color = '#2275B8'
      }

      if (status == 'complete') {
        color = '#769D3E'
      }

      if (config.type == 'pipeline' || config.type == 'stage') {
        if (event.content?.execution?.trigger?.buildInfo?.url) {
          buildInfo = """ build <${event.content.execution.trigger.buildInfo.url}|${
            event.content.execution.trigger.buildInfo.number as Integer
          }> """
        }
      }

      log.info('Sending Slack message {} for {} {} {} {}', kv('address', preference.address), kv('application', application), kv('type', config.type), kv('status', status), kv('executionId', event.content?.execution?.id))

      String body = ''

      if (config.type == 'stage') {
        body = """Stage ${event.content?.context?.stageDetails.name} for """
      }

      String link = "${spinnakerUrl}/#/applications/${application}/${config.type == 'stage' ? 'executions/details' : config.link }/${event.content?.execution?.id}"

      body +=
        """${WordUtils.capitalize(application)}'s <${link}|${
          event.content?.execution?.name ?: event.content?.execution?.description
        }>${buildInfo}${config.type == 'task' ? 'task' : 'pipeline'} ${status == 'starting' ? 'is' : 'has'} ${
          status == 'complete' ? 'completed successfully' : status
        }"""

      if (preference.message?."$config.type.$status"?.text) {
        body += "\n\n" + preference.message."$config.type.$status".text
      }

      String customMessage = event.content?.context?.customMessage
      if (customMessage) {
        body = customMessage
          .replace("{{executionId}}", (String) event.content.execution?.id ?: "")
          .replace("{{link}}", link ?: "")
      }

      String address = preference.address.startsWith('#') ? preference.address : "#${preference.address}"

      slackService.sendMessage(token, new SlackMessage(body, color).buildMessage(), address, true)

    } catch (Exception e) {
      log.error('failed to send slack message ', e)
    }
  }

  @Override
  String getNotificationType() {
    'slack'
  }

}

