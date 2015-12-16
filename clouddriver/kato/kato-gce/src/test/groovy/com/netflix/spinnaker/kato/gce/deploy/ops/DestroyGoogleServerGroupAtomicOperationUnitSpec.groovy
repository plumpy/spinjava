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
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.Operation
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import com.netflix.spinnaker.kato.gce.deploy.description.DestroyGoogleServerGroupDescription
import spock.lang.Specification
import spock.lang.Subject

class DestroyGoogleServerGroupAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final INSTANCE_TEMPLATE_NAME = "$SERVER_GROUP_NAME-${System.currentTimeMillis()}"
  private static final INSTANCE_GROUP_OP_NAME = "spinnaker-test-v000-op"
  private static final ZONE = "us-central1-b"
  private static final DONE = "DONE"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should delete managed instance group"() {
    setup:
      def computeMock = Mock(Compute)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Compute.InstanceGroupManagers.Get)
      def zoneOperations = Mock(Compute.ZoneOperations)
      def zoneOperationsGet = Mock(Compute.ZoneOperations.Get)
      def instanceGroupManager = new InstanceGroupManager()
      instanceGroupManager.setInstanceTemplate(INSTANCE_TEMPLATE_NAME)
      def instanceGroupManagersDeleteMock = Mock(Compute.InstanceGroupManagers.Delete)
      def instanceGroupManagersDeleteOp = new Operation(
          name: INSTANCE_GROUP_OP_NAME,
          status: DONE)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new DestroyGoogleServerGroupDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                zone: ZONE,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new DestroyGoogleServerGroupAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.delete(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersDeleteMock
      1 * instanceGroupManagersDeleteMock.execute() >> instanceGroupManagersDeleteOp

      1 * computeMock.zoneOperations() >> zoneOperations
      1 * zoneOperations.get(PROJECT_NAME, ZONE, INSTANCE_GROUP_OP_NAME) >> zoneOperationsGet
      1 * zoneOperationsGet.execute() >> instanceGroupManagersDeleteOp

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.delete(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesDeleteMock
      1 * instanceTemplatesDeleteMock.execute()
  }
}
