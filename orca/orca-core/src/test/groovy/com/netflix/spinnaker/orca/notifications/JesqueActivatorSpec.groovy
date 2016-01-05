/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import net.greghaines.jesque.worker.WorkerPool
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.*

@Unroll
class JesqueActivatorSpec extends Specification {

  def jesquePool = Mock(WorkerPool)
  @Subject activator = new JesqueActivator(jesquePool)

  def "activator starts the pool when the application goes from #previousStatus to #newStatus"() {
    when:
    activator.onApplicationEvent(event)

    then:
    1 * jesquePool.togglePause(false)

    where:
    previousStatus | newStatus
    DOWN           | UP
    STARTING       | UP
    OUT_OF_SERVICE | UP

    event = new RemoteStatusChangedEvent(new StatusChangeEvent(previousStatus, newStatus))
  }

  def "activator stops the pool when the application goes from #previousStatus to #newStatus"() {
    when:
    activator.onApplicationEvent(event)

    then:
    1 * jesquePool.togglePause(true)

    where:
    previousStatus | newStatus
    UP             | OUT_OF_SERVICE
    UP             | DOWN
    UP             | UNKNOWN
    UNKNOWN        | DOWN

    event = new RemoteStatusChangedEvent(new StatusChangeEvent(previousStatus, newStatus))
  }
}
