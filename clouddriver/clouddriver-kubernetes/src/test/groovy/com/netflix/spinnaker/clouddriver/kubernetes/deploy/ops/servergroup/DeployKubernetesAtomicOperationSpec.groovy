/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesResourceDescription
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.dsl.internal.ReplicationControllerOperationsImpl
import io.fabric8.kubernetes.client.dsl.internal.ServiceOperationsImpl
import spock.lang.Specification
import spock.lang.Subject

class DeployKubernetesAtomicOperationSpec extends Specification {
  private static final NAMESPACE = "default"
  private static final APPLICATION = "app"
  private static final STACK = "stack"
  private static final DETAILS = "details"
  private static final SEQUENCE = "v000"
  private static final TARGET_SIZE = 3
  private static final REGISTRY = 'index.docker.io'
  private static final TAG = 'latest'
  private static final REPOSITORY = 'library/nginx'
  private static final LOAD_BALANCER_NAMES = ["lb1", "lb2"]
  private static final SECURITY_GROUP_NAMES = ["sg1", "sg2", "sg3"]
  private static final CONTAINER_NAMES = ["c1", "c2"]
  private static final REQUEST_CPU = ["100m", null]
  private static final REQUEST_MEMORY = ["100Mi", "200Mi"]
  private static final LIMIT_CPU = ["120m", "200m"]
  private static final LIMIT_MEMORY = ["200Mi", "300Mi"]
  private static final DOCKER_REGISTRY_ACCOUNTS = [new LinkedDockerRegistryConfiguration(accountName: "my-docker-account")]

  def apiMock
  def credentials
  def containers
  def description
  def replicationControllerOperationsMock
  def replicationControllerListMock
  def replicationControllerMock

  def serviceOperationsMock
  def serviceListMock
  def serviceMock
  def serviceSpecMock
  def servicePortMock
  def metadataMock

  def intOrStringMock

  def clusterName
  def replicationControllerName
  def imageId

  def accountCredentialsRepositoryMock

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    apiMock = Mock(KubernetesApiAdaptor)
    replicationControllerOperationsMock = Mock(ReplicationControllerOperationsImpl)
    replicationControllerListMock = Mock(ReplicationControllerList)
    replicationControllerMock = Mock(ReplicationController)
    serviceOperationsMock = Mock(ServiceOperationsImpl)
    serviceListMock = Mock(ServiceList)
    serviceMock = Mock(Service)
    serviceSpecMock = Mock(ServiceSpec)
    servicePortMock = Mock(ServicePort)
    metadataMock = Mock(ObjectMeta)
    intOrStringMock = Mock(IntOrString)
    accountCredentialsRepositoryMock = Mock(AccountCredentialsRepository)

    imageId = KubernetesUtil.getImageId(REGISTRY, REPOSITORY, TAG)
    def imageDescription = KubernetesUtil.buildImageDescription(imageId)

    DOCKER_REGISTRY_ACCOUNTS.forEach({ account ->
      def dockerRegistryAccountMock = Mock(DockerRegistryNamedAccountCredentials)
      accountCredentialsRepositoryMock.getOne(account.accountName) >> dockerRegistryAccountMock
      dockerRegistryAccountMock.getAccountName() >> account
      apiMock.getSecret(NAMESPACE, account.accountName) >> null
      apiMock.createSecret(NAMESPACE, _) >> null
    })

    credentials = new KubernetesCredentials(apiMock, [NAMESPACE], DOCKER_REGISTRY_ACCOUNTS, accountCredentialsRepositoryMock)
    clusterName = KubernetesUtil.combineAppStackDetail(APPLICATION, STACK, DETAILS)
    replicationControllerName = String.format("%s-v%s", clusterName, SEQUENCE)

    containers = []
    CONTAINER_NAMES.eachWithIndex { name, idx ->
      def requests = new KubernetesResourceDescription(cpu: REQUEST_CPU[idx], memory: REQUEST_MEMORY[idx])
      def limits = new KubernetesResourceDescription(cpu: LIMIT_CPU[idx], memory: LIMIT_MEMORY[idx])
      containers = containers << new KubernetesContainerDescription(name: name, imageDescription: imageDescription, requests: requests, limits: limits)
    }
  }

  void "should deploy a replication controller"() {
    setup:
      description = new DeployKubernetesAtomicOperationDescription(
        application: APPLICATION,
        stack: STACK,
        freeFormDetails: DETAILS,
        targetSize: TARGET_SIZE,
        loadBalancers: LOAD_BALANCER_NAMES,
        securityGroups: SECURITY_GROUP_NAMES,
        containers: containers,
        credentials: credentials
      )

      @Subject def operation = new DeployKubernetesAtomicOperation(description)

    when:
      operation.operate([])

    then:

      1 * apiMock.getReplicationControllers(NAMESPACE) >> []
      5 * replicationControllerMock.getMetadata() >> metadataMock
      3 * metadataMock.getName() >> replicationControllerName
      1 * apiMock.createReplicationController(NAMESPACE, { ReplicationController rc ->
        LOAD_BALANCER_NAMES.each { name ->
          assert(rc.metadata.labels[KubernetesUtil.loadBalancerKey(name)])
        }

        SECURITY_GROUP_NAMES.each { name ->
          assert(rc.metadata.labels[KubernetesUtil.securityGroupKey(name)])
        }

        LOAD_BALANCER_NAMES.each { name ->
          assert(rc.spec.template.metadata.labels[KubernetesUtil.loadBalancerKey(name)])
        }

        SECURITY_GROUP_NAMES.each { name ->
          assert(rc.spec.template.metadata.labels[KubernetesUtil.securityGroupKey(name)])
        }

        assert(rc.spec.replicas == TARGET_SIZE)

        CONTAINER_NAMES.eachWithIndex { name, idx ->
          assert(rc.spec.template.spec.containers[idx].name == name)
          assert(rc.spec.template.spec.containers[idx].image == imageId)
          assert(rc.spec.template.spec.containers[idx].resources.requests.cpu == REQUEST_CPU[idx])
          assert(rc.spec.template.spec.containers[idx].resources.requests.memory == REQUEST_MEMORY[idx])
          assert(rc.spec.template.spec.containers[idx].resources.limits.cpu == LIMIT_CPU[idx])
          assert(rc.spec.template.spec.containers[idx].resources.limits.memory == LIMIT_MEMORY[idx])
        }
      }) >> replicationControllerMock
  }
}
