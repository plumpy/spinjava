/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceGroupManagersDeleteInstancesRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.description.TerminateAndDecrementGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import spock.lang.Specification
import spock.lang.Subject

class TerminateAndDecrementGoogleServerGroupAtomicOperationUnitSpec extends Specification {
  private static final SERVER_GROUP_NAME = "my-server-group"
  private static final SERVER_GROUP_SELF_LINK =
    "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instanceGroupManagers/$SERVER_GROUP_NAME"
  private static final REGION = "us-central1"
  private static final ZONE = "us-central1-f"
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final INSTANCE_IDS = ["my-app7-dev-v000-1", "my-app7-dev-v000-2"]
  private static final INSTANCE_URLS = [
    "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instances/my-app7-dev-v000-1",
    "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instances/my-app7-dev-v000-2"
  ]

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should terminate instances"() {
    setup:
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(zone: ZONE).view
      def computeMock = Mock(Compute)
      def request = new InstanceGroupManagersDeleteInstancesRequest().setInstances(INSTANCE_URLS)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def instanceGroupManager = new InstanceGroupManager(selfLink: SERVER_GROUP_SELF_LINK)
      def instanceGroupManagersDeleteInstancesMock = Mock(Compute.InstanceGroupManagers.DeleteInstances)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new TerminateAndDecrementGoogleServerGroupDescription(
          serverGroupName: SERVER_GROUP_NAME,
          region: REGION,
          instanceIds: INSTANCE_IDS,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new TerminateAndDecrementGoogleServerGroupAtomicOperation(description)
      operation.googleClusterProvider = googleClusterProviderMock

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.deleteInstances(PROJECT_NAME,
                                                    ZONE,
                                                    SERVER_GROUP_NAME,
                                                    request) >> instanceGroupManagersDeleteInstancesMock
      1 * instanceGroupManagersDeleteInstancesMock.execute()
  }
}
