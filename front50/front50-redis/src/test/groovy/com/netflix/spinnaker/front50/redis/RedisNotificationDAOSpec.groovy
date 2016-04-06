/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.redis

import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel
import com.netflix.spinnaker.front50.model.notification.Notification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ RedisTestHelper.redisUnavailable() })
@WebAppConfiguration
@ContextConfiguration(classes = [RedisSetup])
class RedisNotificationDAOSpec extends Specification {

  @Configuration
  @Import(RedisConfig)
  static class RedisSetup {

  }

  @Autowired
  RedisNotificationDAO redisNotificationDAO

  void setupSpec() {
    System.setProperty('spinnaker.redis.enabled', 'true')
  }

  void setup() {
    deleteAll()
  }

  void 'globals can be saved, retrieved, and overwritten'() {
    when:
    redisNotificationDAO.saveGlobal(
         [email: [
            [address: 'tyrionl@netflix.com', when: ['pipeline.failed']]
        ]
        ] as Notification
    )

    and:
    Map global = redisNotificationDAO.getGlobal()

    then:
    global.email.size() == 1
    global.email[0].address == 'tyrionl@netflix.com'
    //global.email[0].level == 'global'

    when:
    redisNotificationDAO.saveGlobal(
        [email: [
            [address: 'tywinl@netflix.com', when: ['tasks.failed']]
        ]
        ] as Notification
    )

    and:
    global = redisNotificationDAO.getGlobal()

    then:
    global.email.size() == 1
    global.email[0].address == 'tywinl@netflix.com'
    //global.email[0].level == 'global'

    when:
    def foundNotifications = redisNotificationDAO.all()

    then:
    //foundProjects == [project] // TODO: Handle nested types not indexed
    foundNotifications.size() == 1
    foundNotifications[0].id == global.id
    foundNotifications[0].email == global.email

    when:
    redisNotificationDAO.delete(HierarchicalLevel.GLOBAL, Notification.GLOBAL_ID)

    then:
    redisNotificationDAO.all().isEmpty()

  }

  void deleteAll() {
    redisNotificationDAO.redisTemplate.delete(RedisNotificationDAO.BOOKING_KEEPING_KEY)
  }

}