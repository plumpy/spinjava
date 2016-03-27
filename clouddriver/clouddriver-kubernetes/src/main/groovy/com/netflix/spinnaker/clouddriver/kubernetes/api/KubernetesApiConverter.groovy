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

package com.netflix.spinnaker.clouddriver.kubernetes.api

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesNamedServicePort
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesHttpIngressPath
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesHttpIngressRuleValue
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesIngressBackend
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesIngressRule
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesIngressRuleValue
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.*
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.extensions.Ingress

class KubernetesApiConverter {
  static KubernetesSecurityGroupDescription fromIngress(Ingress ingress) {
    if (!ingress) {
      return null
    }

    def securityGroupDescription = new KubernetesSecurityGroupDescription()

    securityGroupDescription.securityGroupName = ingress.metadata.name
    def parse = Names.parseName(securityGroupDescription.securityGroupName)
    securityGroupDescription.app = parse.app
    securityGroupDescription.stack = parse.stack
    securityGroupDescription.detail = parse.detail
    securityGroupDescription.namespace = ingress.metadata.namespace

    securityGroupDescription.ingress = new KubernetesIngressBackend()
    securityGroupDescription.ingress.port = ingress.spec.backend?.servicePort?.intVal ?: 0
    securityGroupDescription.ingress.serviceName = ingress.spec.backend?.serviceName

    securityGroupDescription.rules = ingress.spec.rules.collect { rule ->
      def resRule = new KubernetesIngressRule()
      resRule.host = rule.host
      if (rule.http) {
        resRule.value = new KubernetesIngressRuleValue(http: new KubernetesHttpIngressRuleValue())
        resRule.value.http.paths = rule.http.paths?.collect { path ->
          def resPath = new KubernetesHttpIngressPath()
          resPath.path = path.path
          if (path.backend) {
            resPath.ingress = new KubernetesIngressBackend(port: path.backend.servicePort?.intVal ?: 0,
                                                           serviceName: path.backend.serviceName)
          }

          return resPath
        }
      }

      return resRule
    }

    securityGroupDescription
  }


  static KubernetesLoadBalancerDescription fromService(Service service) {
    if (!service) {
      return null
    }

    def loadBalancerDescription = new KubernetesLoadBalancerDescription()

    loadBalancerDescription.name = service.metadata.name
    def parse = Names.parseName(loadBalancerDescription.name)
    loadBalancerDescription.app = parse.app
    loadBalancerDescription.stack = parse.stack
    loadBalancerDescription.detail = parse.detail
    loadBalancerDescription.namespace = service.metadata.namespace

    loadBalancerDescription.clusterIp = service.spec.clusterIP
    loadBalancerDescription.loadBalancerIp = service.spec.loadBalancerIP
    loadBalancerDescription.sessionAffinity = service.spec.sessionAffinity
    loadBalancerDescription.serviceType = service.spec.type

    loadBalancerDescription.externalIps = service.spec.externalIPs ?: []
    loadBalancerDescription.ports = service.spec.ports?.collect { port ->
      new KubernetesNamedServicePort(
          name: port.name,
          protocol: port.protocol,
          port: port.port ?: 0,
          targetPort: port.targetPort?.intVal ?: 0,
          nodePort: port.nodePort ?: 0
      )
    }

    return loadBalancerDescription
  }

  static KubernetesContainerDescription fromContainer(Container container) {
    if (!container) {
      return null
    }

    def containerDescription = new KubernetesContainerDescription()
    containerDescription.name = container.name
    containerDescription.imageDescription = KubernetesUtil.buildImageDescription(container.image)

    container.resources?.with {
      containerDescription.limits = limits?.cpu?.amount || limits?.memory?.amount ?
          new KubernetesResourceDescription(
              cpu: limits?.cpu?.amount,
              memory: limits?.memory?.amount
          ) : null

      containerDescription.requests = requests?.cpu?.amount || requests?.memory?.amount ?
          new KubernetesResourceDescription(
              cpu: requests?.cpu?.amount,
              memory: requests?.memory?.amount
          ) : null
    }

    containerDescription.ports = container.ports?.collect {
      def port = new KubernetesContainerPort()
      port.hostIp = it?.hostIP
      if (it?.hostPort) {
        port.hostPort = it?.hostPort?.intValue()
      }
      if (it?.containerPort) {
        port.containerPort = it?.containerPort?.intValue()
      }
      port.name = it?.name
      port.protocol = it?.protocol

      return port
    }

    containerDescription.livenessProbe = fromProbe(container?.livenessProbe)
    containerDescription.readinessProbe = fromProbe(container?.readinessProbe)

    containerDescription.envVars = container?.env?.collect { envVar ->
      new KubernetesEnvVar(name: envVar.name, value: envVar.value)
    }

    containerDescription.volumeMounts = container?.volumeMounts?.collect { volumeMount ->
      new KubernetesVolumeMount(name: volumeMount.name, readOnly: volumeMount.readOnly, mountPath: volumeMount.mountPath)
    }

    containerDescription.args = container?.args ?: []
    containerDescription.command = container?.command ?: []

    return containerDescription
  }

  static DeployKubernetesAtomicOperationDescription fromReplicationController(ReplicationController replicationController) {
    def deployDescription = new DeployKubernetesAtomicOperationDescription()
    def parsedName = Names.parseName(replicationController?.metadata?.name)

    deployDescription.application = parsedName?.app
    deployDescription.stack = parsedName?.stack
    deployDescription.freeFormDetails = parsedName?.detail
    deployDescription.loadBalancers = KubernetesUtil?.getDescriptionLoadBalancers(replicationController)
    deployDescription.namespace = replicationController?.metadata?.namespace
    deployDescription.targetSize = replicationController?.spec?.replicas
    deployDescription.securityGroups = []

    deployDescription.volumeSources = replicationController?.spec?.template?.spec?.volumes?.collect { volume ->
      def res = new KubernetesVolumeSource(name: volume.name)

      if (volume.emptyDir) {
        res.type = KubernetesVolumeSourceType.EMPTYDIR
        def medium = volume.emptyDir.medium
        def mediumType

        if (medium == "Memory") {
          mediumType = KubernetesStorageMediumType.MEMORY
        } else {
          mediumType = KubernetesStorageMediumType.DEFAULT
        }

        res.emptyDir = new KubernetesEmptyDir(medium: mediumType)
      } else if (volume.hostPath) {
        res.type = KubernetesVolumeSourceType.HOSTPATH
        res.hostPath = new KubernetesHostPath(path: volume.hostPath.path)
      } else if (volume.persistentVolumeClaim) {
        res.type = KubernetesVolumeSourceType.PERSISTENTVOLUMECLAIM
        res.persistentVolumeClaim = new KubernetesPersistentVolumeClaim(claimName: volume.persistentVolumeClaim.claimName,
            readOnly: volume.persistentVolumeClaim.readOnly)
      } else if (volume.secret) {
        res.type = KubernetesVolumeSourceType.SECRET
        res.secret = new KubernetesSecretVolumeSource(secretName: volume.secret.secretName)
      } else {
        res.type = KubernetesVolumeSourceType.UNSUPPORTED
      }

      return res
    } ?: []

    deployDescription.containers = replicationController?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    }

    return deployDescription
  }

  static KubernetesProbe fromProbe(Probe probe) {
    if (!probe) {
      return null
    }

    def kubernetesProbe = new KubernetesProbe()
    kubernetesProbe.failureThreshold = probe.failureThreshold ?: 0
    kubernetesProbe.successThreshold = probe.successThreshold ?: 0
    kubernetesProbe.timeoutSeconds = probe.timeoutSeconds ?: 0
    kubernetesProbe.periodSeconds = probe.periodSeconds ?: 0
    kubernetesProbe.initialDelaySeconds = probe.initialDelaySeconds ?: 0
    kubernetesProbe.handler = new KubernetesHandler()

    if (probe.exec) {
      kubernetesProbe.handler.execAction = fromExecAction(probe.exec)
      kubernetesProbe.handler.type = KubernetesHandlerType.EXEC
    }

    if (probe.tcpSocket) {
      kubernetesProbe.handler.tcpSocketAction = fromTcpSocketAction(probe.tcpSocket)
      kubernetesProbe.handler.type = KubernetesHandlerType.TCP
    }

    if (probe.httpGet) {
      kubernetesProbe.handler.httpGetAction = fromHttpGetAction(probe.httpGet)
      kubernetesProbe.handler.type = KubernetesHandlerType.HTTP
    }

    return kubernetesProbe
  }

  static KubernetesExecAction fromExecAction(ExecAction exec) {
    if (!exec) {
      return null
    }

    def kubernetesExecAction = new KubernetesExecAction()
    kubernetesExecAction.commands = exec.command
    return kubernetesExecAction
  }

  static KubernetesTcpSocketAction fromTcpSocketAction(TCPSocketAction tcpSocket) {
    if (!tcpSocket) {
      return null
    }

    def kubernetesTcpSocketAction = new KubernetesTcpSocketAction()
    kubernetesTcpSocketAction.port = tcpSocket.port?.intVal
    return kubernetesTcpSocketAction
  }

  static KubernetesHttpGetAction fromHttpGetAction(HTTPGetAction httpGet) {
    if (!httpGet) {
      return null
    }

    def kubernetesHttpGetAction = new KubernetesHttpGetAction()
    kubernetesHttpGetAction.host = httpGet.host
    kubernetesHttpGetAction.path = httpGet.path
    kubernetesHttpGetAction.port = httpGet.port?.intVal
    kubernetesHttpGetAction.uriScheme = httpGet.scheme
    return kubernetesHttpGetAction
  }
}
