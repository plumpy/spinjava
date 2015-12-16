/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.kato.cf.deploy.ops

import com.netflix.spinnaker.kato.cf.TestCredential
import com.netflix.spinnaker.kato.cf.deploy.description.DestroyCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.cf.security.TestCloudFoundryClientFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import org.cloudfoundry.client.lib.CloudFoundryOperations
import org.springframework.web.client.ResourceAccessException
import spock.lang.Specification

class DestroyCloudFoundryServerGroupAtomicOperationSpec extends Specification {

  CloudFoundryOperations client
  CloudFoundryOperations clientForNonExistentServerGroup

  def setup() {
    TaskRepository.threadLocalTask.set(Mock(Task))

    client = Mock(CloudFoundryOperations)

    clientForNonExistentServerGroup = Mock(CloudFoundryOperations)
  }

  void "should not fail delete when server group does not exist"() {
    given:
    1 * clientForNonExistentServerGroup.deleteApplication(_) >> { throw new ResourceAccessException("app doesn't exist") }
    0 * clientForNonExistentServerGroup._

    def op = new DestroyCloudFoundryServerGroupAtomicOperation(
        new DestroyCloudFoundryServerGroupDescription(
            serverGroupName: "my-stack-v000",
            zone: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: clientForNonExistentServerGroup)

    when:
    op.operate([])

    then:
    notThrown(Exception)
  }

  void "should delete server group"() {
    setup:
    def op = new DestroyCloudFoundryServerGroupAtomicOperation(
        new DestroyCloudFoundryServerGroupDescription(
            serverGroupName: "my-stack-v000",
            zone: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)

    when:
    op.operate([])

    then:
    1 * client.deleteApplication("my-stack-v000")
    0 * client._
  }

}
