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

package com.netflix.spinnaker.echo.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.rest.RestService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Event listener for echo events
 */
@Component
@Slf4j
@ConditionalOnProperty('rest.enabled')
@SuppressWarnings('GStringExpressionWithinString')
class RestEventListener implements EchoEventListener {

  ObjectMapper mapper = new ObjectMapper()

  @Autowired
  RestService restService

  @Value('rest.eventName')
  String eventName

  @Override
  void processEvent(Event event) {
    Map eventAsMap = mapper.convertValue(event, Map)
    restService.recordEvent([
      "eventName": eventName,
      "payload"  : eventAsMap
    ])
  }
}
