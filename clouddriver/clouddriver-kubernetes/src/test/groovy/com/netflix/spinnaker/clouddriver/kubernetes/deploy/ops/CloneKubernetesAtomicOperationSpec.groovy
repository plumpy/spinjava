/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.CloneKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesResourceDescription
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec
import io.fabric8.kubernetes.api.model.ResourceRequirements
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder
import spock.lang.Specification
import spock.lang.Subject

class CloneKubernetesAtomicOperationSpec extends Specification {
  private static final APPLICATION = "myapp"
  private static final STACK = "test"
  private static final DETAIL = "mdservice"
  private static final NAMESPACE1 = "default"
  private static final NAMESPACE2 = "nondefault"
  private static final SEQUENCE = "v000"
  private static final TARGET_SIZE = 2
  private static final LOAD_BALANCER_NAMES = ["lb1", "lb2"]
  private static final SECURITY_GROUP_NAMES = ["sg1", "sg2"]
  private static final LABELS = ["load-balancer-lb1": true, "load-balancer-lb2": true, "security-group-sg1": true, "security-group-sg2": true]
  private static final CONTAINER_NAMES = ["c1", "c2"]
  private static final REQUEST_CPU = ["100m", null]
  private static final REQUEST_MEMORY = ["100Mi", "200Mi"]
  private static final LIMIT_CPU = ["120m", "200m"]
  private static final LIMIT_MEMORY = ["200Mi", "300Mi"]
  private static final ANCESTOR_SERVER_GROUP_NAME = "$APPLICATION-$STACK-$DETAIL-$SEQUENCE"

  def containers
  def ancestorNames
  def expectedResultDescription
  def replicationController
  def replicationControllerSpec
  def podTemplateSpec
  def objectMetadata
  def podSpec
  def kubernetesUtilMock
  def replicationControllerContainers

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    kubernetesUtilMock = Mock(KubernetesUtil)

    containers = []
    CONTAINER_NAMES.eachWithIndex { name, idx ->
      def requests = new KubernetesResourceDescription(cpu: REQUEST_CPU[idx], memory: REQUEST_MEMORY[idx])
      def limits = new KubernetesResourceDescription(cpu: LIMIT_CPU[idx], memory: LIMIT_MEMORY[idx])
      containers = containers << new KubernetesContainerDescription(name: name, image: name, requests: requests, limits: limits)
    }

    ancestorNames = [
      "app": APPLICATION,
      "stack": STACK,
      "detail": DETAIL
    ]

    expectedResultDescription = new CloneKubernetesAtomicOperationDescription(
      application: APPLICATION,
      stack: STACK,
      freeFormDetails: DETAIL,
      targetSize: TARGET_SIZE,
      loadBalancers: LOAD_BALANCER_NAMES,
      securityGroups: SECURITY_GROUP_NAMES,
      containers: containers,
      namespace: NAMESPACE1
    )

    replicationController = new ReplicationController()
    replicationControllerSpec = new ReplicationControllerSpec()
    podTemplateSpec= new PodTemplateSpec()
    objectMetadata = new ObjectMeta()
    podSpec = new PodSpec()

    objectMetadata.setLabels(LABELS)
    podTemplateSpec.setMetadata(objectMetadata)
    replicationControllerSpec.setTemplate(podTemplateSpec)

    replicationControllerContainers = []
    containers = []
    def l = CONTAINER_NAMES.size()
    CONTAINER_NAMES.eachWithIndex { name, idx ->
      def container = new Container()
      container.setName(name)
      container.setImage(name)

      def requestsBuilder = new ResourceRequirementsBuilder()
      // Rotate indices to ensure they are overwritten by request
      requestsBuilder = requestsBuilder.addToLimits([cpu: new Quantity(LIMIT_CPU[l - idx]), memory: new Quantity(LIMIT_MEMORY[l - idx])])
      requestsBuilder = requestsBuilder.addToRequests([cpu: new Quantity(REQUEST_CPU[l - idx]), memory: new Quantity(REQUEST_MEMORY[l - idx])])
      container.setResources(requestsBuilder.build())
      replicationControllerContainers = replicationControllerContainers << container

      def requests = new KubernetesResourceDescription(cpu: REQUEST_CPU[l - idx], memory: REQUEST_MEMORY[l - idx])
      def limits = new KubernetesResourceDescription(cpu: LIMIT_CPU[l - idx], memory: LIMIT_MEMORY[l - idx])
      containers = containers << new KubernetesContainerDescription(name: name, image: name, requests: requests, limits: limits)
    }

    podSpec.setContainers(replicationControllerContainers)
    podTemplateSpec.setSpec(podSpec)
    replicationControllerSpec.setReplicas(TARGET_SIZE)
    replicationController.setSpec(replicationControllerSpec)
  }

  void "builds a description based on ancestor server group, overrides nothing"() {
    setup:
      def inputDescription = new CloneKubernetesAtomicOperationDescription(
        source: [serverGroupName: ANCESTOR_SERVER_GROUP_NAME, namespace: NAMESPACE1]
      )

      @Subject def operation = new CloneKubernetesAtomicOperation(inputDescription)

      kubernetesUtilMock.getReplicationController(inputDescription.kubernetesCredentials, NAMESPACE1, inputDescription.source.serverGroupName) >> replicationController
      operation.kubernetesUtil = kubernetesUtilMock

    when:
      def resultDescription = operation.cloneAndOverrideDescription()

    then:
      resultDescription.application == expectedResultDescription.application
      resultDescription.stack == expectedResultDescription.stack
      resultDescription.freeFormDetails == expectedResultDescription.freeFormDetails
      resultDescription.targetSize == expectedResultDescription.targetSize
      resultDescription.loadBalancers == expectedResultDescription.loadBalancers
      resultDescription.securityGroups == expectedResultDescription.securityGroups
      resultDescription.namespace == expectedResultDescription.namespace
      resultDescription.containers.eachWithIndex { c, idx ->
        c.image == expectedResultDescription.containers[idx].image
        c.name == expectedResultDescription.containers[idx].name
        c.requests.cpu == expectedResultDescription.containers[idx].requests.cpu
        c.requests.memory == expectedResultDescription.containers[idx].requests.memory
        c.limits.cpu == expectedResultDescription.containers[idx].limits.cpu
        c.limits.memory == expectedResultDescription.containers[idx].limits.memory
      }
  }

  void "operation builds a description based on ancestor server group, overrides everything"() {
    setup:
      def inputDescription = new CloneKubernetesAtomicOperationDescription(
        application: APPLICATION,
        stack: STACK,
        namespace: NAMESPACE1,
        freeFormDetails: DETAIL,
        targetSize: TARGET_SIZE,
        loadBalancers: LOAD_BALANCER_NAMES,
        securityGroups: SECURITY_GROUP_NAMES,
        containers: containers,
        source: [serverGroupName: ANCESTOR_SERVER_GROUP_NAME, namespace: NAMESPACE2]
      )

      @Subject def operation = new CloneKubernetesAtomicOperation(inputDescription)

      kubernetesUtilMock.getReplicationController(inputDescription.kubernetesCredentials, NAMESPACE2, inputDescription.source.serverGroupName) >> replicationController
      operation.kubernetesUtil = kubernetesUtilMock

    when:
      def resultDescription = operation.cloneAndOverrideDescription()

    then:
      resultDescription.application == expectedResultDescription.application
      resultDescription.stack == expectedResultDescription.stack
      resultDescription.freeFormDetails == expectedResultDescription.freeFormDetails
      resultDescription.targetSize == expectedResultDescription.targetSize
      resultDescription.loadBalancers == expectedResultDescription.loadBalancers
      resultDescription.securityGroups == expectedResultDescription.securityGroups
      resultDescription.namespace == expectedResultDescription.namespace
      resultDescription.containers.eachWithIndex { c, idx ->
        c.image == expectedResultDescription.containers[idx].image
        c.name == expectedResultDescription.containers[idx].name
        c.requests.cpu == expectedResultDescription.containers[idx].requests.cpu
        c.requests.memory == expectedResultDescription.containers[idx].requests.memory
        c.limits.cpu == expectedResultDescription.containers[idx].limits.cpu
        c.limits.memory == expectedResultDescription.containers[idx].limits.memory
      }
  }
}
