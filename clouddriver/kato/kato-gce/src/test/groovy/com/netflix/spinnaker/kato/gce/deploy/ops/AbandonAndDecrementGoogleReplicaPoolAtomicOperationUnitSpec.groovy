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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceGroupManagersAbandonInstancesRequest
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.AbandonAndDecrementGoogleServerGroupDescription
import spock.lang.Specification
import spock.lang.Subject

class AbandonAndDecrementGoogleReplicaPoolAtomicOperationUnitSpec extends Specification {
  private static final REPLICA_POOL_NAME = "my-replica-pool"
  private static final REPLICA_POOL_SELF_LINK =
    "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instanceGroupManagers/$REPLICA_POOL_NAME"
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
      def computeMock = Mock(Compute)
      def request = new InstanceGroupManagersAbandonInstancesRequest().setInstances(INSTANCE_URLS)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def instanceGroupManager = new InstanceGroupManager(selfLink: REPLICA_POOL_SELF_LINK)
      def instanceGroupManagersAbandonInstancesMock = Mock(Compute.InstanceGroupManagers.AbandonInstances)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock, null, null, null)
      def description = new AbandonAndDecrementGoogleServerGroupDescription(
          replicaPoolName: REPLICA_POOL_NAME,
          zone: ZONE,
          instanceIds: INSTANCE_IDS,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new AbandonAndDecrementGoogleServerGroupAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.abandonInstances(PROJECT_NAME,
                                                     ZONE,
                                                     REPLICA_POOL_NAME,
                                                     request) >> instanceGroupManagersAbandonInstancesMock
      1 * instanceGroupManagersAbandonInstancesMock.execute()
  }
}
