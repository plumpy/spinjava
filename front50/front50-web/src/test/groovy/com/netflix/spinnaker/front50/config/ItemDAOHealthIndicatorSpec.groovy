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


package com.netflix.spinnaker.front50.config

import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import org.springframework.boot.actuate.health.Status
import spock.lang.Shared
import spock.lang.Specification

class ItemDAOHealthIndicatorSpec extends Specification {
  @Shared
  ItemDAOHealthIndicator healthCheck

  @Shared
  ApplicationDAO dao

  void setup() {
    dao = Mock(ApplicationDAO)
    healthCheck = new ItemDAOHealthIndicator(itemDAO: dao)
  }

  void 'health check should return 5xx error if dao is not working'() {
    when:
    healthCheck.pollForHealth()
    def result = healthCheck.health()

    then:
    1 * dao.isHealthy() >> false
    result.status == Status.DOWN
  }

  void 'health check should return Ok'() {
    when:
    healthCheck.pollForHealth()
    def result = healthCheck.health()

    then:
    1 * dao.isHealthy() >> true
    result.status == Status.UP
  }
}
