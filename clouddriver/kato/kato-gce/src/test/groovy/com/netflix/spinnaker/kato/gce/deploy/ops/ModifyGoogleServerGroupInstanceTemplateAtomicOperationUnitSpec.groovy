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
import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.AttachedDiskInitializeParams
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.Metadata
import com.google.api.services.compute.model.NetworkInterface
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.Tags
import com.google.api.services.replicapool.Replicapool
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.util.ReplicaPoolBuilder
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import com.netflix.spinnaker.kato.gce.deploy.description.ModifyGoogleServerGroupInstanceTemplateDescription
import com.netflix.spinnaker.kato.gce.deploy.exception.GoogleOperationException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

// After GCEUtil is refactored to be dependency-injected:
//  TODO(duftler): Add test to verify that referenced GCE resources are queried.
//  TODO(duftler): Add test to verify that instance template is not created if a referenced resource is invalid.
class ModifyGoogleServerGroupInstanceTemplateAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REPLICA_POOL_NAME = "spinnaker-test-v000"
  private static final ZONE = "us-central1-b"

  private static final MACHINE_TYPE = "f1-micro"
  private static final NETWORK_1 = "default"
  private static final NETWORK_2 = "other-network"
  private static final IMAGE = "debian"
  private static final DISK_TYPE = "pd-standard"
  private static final DISK_SIZE_GB = 120
  private static final METADATA_1 = ["startup-script": "sudo apt-get update"]
  private static final METADATA_2 = ["some-key": "some-value"]
  private static final TAGS_1 = ["some-tag-1", "some-tag-2", "some-tag-3"]
  private static final TAGS_2 = ["some-tag-4", "some-tag-5"]
  private static final ORIG_INSTANCE_TEMPLATE_NAME = "$REPLICA_POOL_NAME-123"
  private static final ORIG_INSTANCE_TEMPLATE_URL =
      "https://www.googleapis.com/compute/v1/projects/$PROJECT_NAME/global/instanceTemplates/$ORIG_INSTANCE_TEMPLATE_NAME"
  private static final NEW_INSTANCE_TEMPLATE_NAME = "new-instance-template"
  private static final INSTANCE_TEMPLATE_INSERTION_OP_NAME = "instance-template-insertion-op"
  private static final SET_INSTANCE_TEMPLATE_OP_NAME = "set-instance-template-op"
  private static final DONE = "DONE"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should not make any changes if no properties are overridden"() {
    setup:
      def computeMock = Mock(Compute)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(
        properties: new InstanceProperties(
          machineType: MACHINE_TYPE,
          networkInterfaces: [
            new NetworkInterface(network: NETWORK_1)
          ],
          disks: [
            new AttachedDisk(initializeParams: new AttachedDiskInitializeParams(
              sourceImage: IMAGE,
              diskType: DISK_TYPE,
              diskSizeGb: DISK_SIZE_GB
            ))
          ],
          metadata: new Metadata(GCEUtil.buildMetadataFromMap(METADATA_1)),
          tags: new Tags(items: TAGS_1)
        )
      )
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: REPLICA_POOL_NAME)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                               zone: ZONE,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description, replicaPoolBuilderMock)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the managed instance group and its instance template.
      1 * replicaPoolBuilderMock.buildReplicaPool(_) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal
  }

  void "should set metadata and tags on new instance template"() {
    setup:
      def computeMock = Mock(Compute)
      def globalOperations = Mock(Compute.GlobalOperations)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(
        properties: new InstanceProperties(
          machineType: MACHINE_TYPE,
          networkInterfaces: [
            new NetworkInterface(network: NETWORK_1)
          ],
          disks: [
            new AttachedDisk(initializeParams: new AttachedDiskInitializeParams(
              sourceImage: IMAGE,
              diskType: DISK_TYPE,
              diskSizeGb: DISK_SIZE_GB
            ))
          ],
          metadata: new Metadata(GCEUtil.buildMetadataFromMap(METADATA_1)),
          tags: new Tags(items: TAGS_1)
        )
      )
      def instanceTemplatesInsertMock = Mock(Compute.InstanceTemplates.Insert)
      def instanceTemplateInsertionOperationGetMock = Mock(Compute.GlobalOperations.Get)
      def instanceTemplateInsertionOperationReal = new Operation(targetLink: NEW_INSTANCE_TEMPLATE_NAME,
                                                                 name: INSTANCE_TEMPLATE_INSERTION_OP_NAME,
                                                                 status: DONE)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def replicaPoolZonalOperations = Mock(Replicapool.ZoneOperations)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: REPLICA_POOL_NAME)
      def setInstanceTemplateMock = Mock(Replicapool.InstanceGroupManagers.SetInstanceTemplate)
      def setInstanceTemplateOperationReal = new Operation(targetLink: REPLICA_POOL_NAME,
                                                           name: SET_INSTANCE_TEMPLATE_OP_NAME,
                                                           status: DONE)
      def setInstanceTemplateOperationGetMock = Mock(Replicapool.ZoneOperations.Get)
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                               zone: ZONE,
                                                                               instanceMetadata: METADATA_2,
                                                                               tags: TAGS_2,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description, replicaPoolBuilderMock)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the managed instance group and its instance template.
      1 * replicaPoolBuilderMock.buildReplicaPool(_) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal

      // Insert the new instance template.
      1 * instanceTemplatesMock.insert(PROJECT_NAME, {
        // Verify that the new instance template has a different name than the original instance template.
        (it.name != ORIG_INSTANCE_TEMPLATE_NAME
          && GCEUtil.buildMapFromMetadata(it.properties.metadata) == METADATA_2
          && it.properties.tags.items == TAGS_2)
      }) >> instanceTemplatesInsertMock
      1 * instanceTemplatesInsertMock.execute() >> instanceTemplateInsertionOperationReal
      1 * computeMock.globalOperations() >> globalOperations
      1 * globalOperations.get(PROJECT_NAME, INSTANCE_TEMPLATE_INSERTION_OP_NAME) >> instanceTemplateInsertionOperationGetMock
      1 * instanceTemplateInsertionOperationGetMock.execute() >> instanceTemplateInsertionOperationReal

      // Set the new instance template on the managed instance group.
      1 * instanceGroupManagersMock.setInstanceTemplate(PROJECT_NAME, ZONE, REPLICA_POOL_NAME, {
        // Verify that the target link of the instance creation operation is used to set the new instance template.
        it.instanceTemplate == NEW_INSTANCE_TEMPLATE_NAME
      }) >> setInstanceTemplateMock
      1 * setInstanceTemplateMock.execute() >> setInstanceTemplateOperationReal
      1 * replicaPoolMock.zoneOperations() >> replicaPoolZonalOperations
      1 * replicaPoolZonalOperations.get(PROJECT_NAME, ZONE, SET_INSTANCE_TEMPLATE_OP_NAME) >> setInstanceTemplateOperationGetMock
      1 * setInstanceTemplateOperationGetMock.execute() >> setInstanceTemplateOperationReal

      // Delete the original instance template.
      1 * instanceTemplatesMock.delete(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesDeleteMock
      1 * instanceTemplatesDeleteMock.execute()
  }

  void "should throw exception if no original instance template properties can be resolved"() {
    setup:
      def computeMock = Mock(Compute)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(name: ORIG_INSTANCE_TEMPLATE_NAME)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: REPLICA_POOL_NAME)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                               zone: ZONE,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description, replicaPoolBuilderMock)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the managed instance group and its instance template.
      1 * replicaPoolBuilderMock.buildReplicaPool(_) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal

      def exc = thrown GoogleOperationException
      exc.message == "Unable to determine properties of instance template $ORIG_INSTANCE_TEMPLATE_NAME."
  }

  @Unroll
  void "should throw exception if the original instance template defines a number of network interfaces other than one"() {
    setup:
      def computeMock = Mock(Compute)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(
        name: ORIG_INSTANCE_TEMPLATE_NAME,
        properties: new InstanceProperties(
          machineType: MACHINE_TYPE,
          networkInterfaces: networkInterfaces,
          disks: [
            new AttachedDisk(initializeParams: new AttachedDiskInitializeParams(
              sourceImage: IMAGE,
              diskType: DISK_TYPE,
              diskSizeGb: DISK_SIZE_GB
            ))
          ],
          metadata: new Metadata(GCEUtil.buildMetadataFromMap(METADATA_1)),
          tags: new Tags(items: TAGS_1)
        )
      )
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: REPLICA_POOL_NAME)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                               zone: ZONE,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description, replicaPoolBuilderMock)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the managed instance group and its instance template.
      1 * replicaPoolBuilderMock.buildReplicaPool(_) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal

      def exc = thrown GoogleOperationException
      exc.message == "Instance templates must have exactly one network interface defined. " +
                     "Instance template $ORIG_INSTANCE_TEMPLATE_NAME has $exceptionMsgSizeDescriptor."

    where:
      networkInterfaces                                                                    | exceptionMsgSizeDescriptor
      null                                                                                 | null
      []                                                                                   | 0
      [new NetworkInterface(network: NETWORK_1), new NetworkInterface(network: NETWORK_2)] | 2
  }

  @Unroll
  void "should throw exception if the original instance template defines a number of disks other than one"() {
    setup:
      def computeMock = Mock(Compute)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesGetMock = Mock(Compute.InstanceTemplates.Get)
      def instanceTemplateReal = new InstanceTemplate(
        name: ORIG_INSTANCE_TEMPLATE_NAME,
        properties: new InstanceProperties(
          machineType: MACHINE_TYPE,
          networkInterfaces: [
            new NetworkInterface(network: NETWORK_1)
          ],
          disks: disks,
          metadata: new Metadata(GCEUtil.buildMetadataFromMap(METADATA_1)),
          tags: new Tags(items: TAGS_1)
        )
      )
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
      def instanceGroupManagerReal = new InstanceGroupManager(instanceTemplate: ORIG_INSTANCE_TEMPLATE_URL, group: REPLICA_POOL_NAME)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                               zone: ZONE,
                                                                               accountName: ACCOUNT_NAME,
                                                                               credentials: credentials)
      @Subject def operation = new ModifyGoogleServerGroupInstanceTemplateAtomicOperation(description, replicaPoolBuilderMock)
      operation.googleOperationPoller =
          new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfigurationProperties())

    when:
      operation.operate([])

    then:
      // Query the managed instance group and its instance template.
      1 * replicaPoolBuilderMock.buildReplicaPool(_) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManagerReal
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.get(PROJECT_NAME, ORIG_INSTANCE_TEMPLATE_NAME) >> instanceTemplatesGetMock
      1 * instanceTemplatesGetMock.execute() >> instanceTemplateReal

      def exc = thrown GoogleOperationException
      exc.message == "Instance templates must have exactly one disk defined. " +
                     "Instance template $ORIG_INSTANCE_TEMPLATE_NAME has $exceptionMsgSizeDescriptor."

    where:
      disks                                    | exceptionMsgSizeDescriptor
      null                                     | null
      []                                       | 0
      [new AttachedDisk(), new AttachedDisk()] | 2
  }
}
