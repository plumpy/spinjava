/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.Compute
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.ResizeGoogleServerGroupDescription
import spock.lang.Specification
import spock.lang.Subject

class ResizeGoogleServerGroupAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final TARGET_SIZE = 5
  private static final ZONE = "us-central1-b"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should resize managed instance group"() {
    setup:
      def computeMock = Mock(Compute)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersResizeMock = Mock(Compute.InstanceGroupManagers.Resize)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock, null, null, null)
      def description = new ResizeGoogleServerGroupDescription(serverGroupName: SERVER_GROUP_NAME,
                                                               targetSize: TARGET_SIZE,
                                                               zone: ZONE,
                                                               accountName: ACCOUNT_NAME,
                                                               credentials: credentials)
      @Subject def operation = new ResizeGoogleServerGroupAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.resize(PROJECT_NAME,
                                           ZONE,
                                           SERVER_GROUP_NAME,
                                           TARGET_SIZE) >> instanceGroupManagersResizeMock
      1 * instanceGroupManagersResizeMock.execute()
  }
}
