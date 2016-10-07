/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.echo.notification

import groovy.json.JsonSlurper
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.slack.SlackService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable

class SlackNotificationAgentSpec extends Specification {

  def slack = Mock(SlackService)
  @Subject def agent = new SlackNotificationAgent(slackService: slack)

  @Unroll
  def "sends correct message for #status status"() {
    given:
    def actualMessage = new BlockingVariable<String>()
    slack.sendMessage(*_) >> { token, message, channel, asUser ->
      actualMessage.set(message)
    }

    when:
    agent.sendNotifications([address: channel], application, event, [type: type, link: "link"], status)

    then:
    new JsonSlurper().parseText(actualMessage.get()).text[0] ==~ expectedMessage

    where:
    status      || expectedMessage
    "completed" || /Whatever's .* pipeline has completed/
    "starting"  || /Whatever's .* pipeline is starting/
    "failed"    || /Whatever's .* pipeline has failed/

    channel = "bluespar"
    application = "whatever"
    event = new Event(content: [execution: [id: "1", name: "foo-pipeline"]])
    type = "pipeline"
  }

  @Unroll
  def "appends custom message to #status message if present"() {
    given:
    def actualMessage = new BlockingVariable<String>()
    slack.sendMessage(*_) >> { token, message, channel, asUser ->
      actualMessage.set(message)
    }

    when:
    agent.sendNotifications([address: channel, message: message], application, event, [type: type, link: "link"], status)

    then:
    new JsonSlurper().parseText(actualMessage.get()).text[0] ==~ expectedMessage

    where:
    status      || expectedMessage
    "completed" || /Whatever's .* pipeline has completed\n\nCustom completed message/
    "starting"  || /Whatever's .* pipeline is starting\n\nCustom starting message/
    "failed"    || /Whatever's .* pipeline has failed\n\nCustom failed message/

    channel = "bluespar"
    application = "whatever"
    event = new Event(content: [execution: [id: "1", name: "foo-pipeline"]])
    type = "pipeline"
    message = ["completed", "starting", "failed"].collectEntries {
      [(it): [text: "Custom $it message"]]
    }
  }
}
