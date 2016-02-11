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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesNamedServicePort
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.UpsertKubernetesLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.StandardKubernetesAttributeValidator
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Specification

class UpsertKubernetesLoadBalancerAtomicOperationValidatorSpec extends Specification {
  final static DESCRIPTION = "upsertKubernetesLoadBalancerAtomicOperationDescription"
  final static List<String> NAMESPACES = ['default', 'prod']
  final static String NAMESPACE = 'prod'
  final static int VALID_PORT = 80
  final static int INVALID_PORT = 104729
  final static String VALID_PROTOCOL = "TCP"
  final static String INVALID_PROTOCOL = "PCT"
  final static String VALID_NAME = "name"
  final static String INVALID_NAME = "bad name ?"
  final static String VALID_IP = "127.0.0.1"
  final static String INVALID_IP = "0.127.0.0.1"
  final static String VALID_CREDENTIALS = "my-kubernetes-account"

  UpsertKubernetesLoadBalancerAtomicOperationValidator validator

  def credentials
  def validPort
  def invalidPortPort
  def invalidNamePort
  def invalidProtocolPort

  void setup() {
    validator = new UpsertKubernetesLoadBalancerAtomicOperationValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def namedCredentialsMock = Mock(KubernetesNamedAccountCredentials)

    def apiMock = Mock(KubernetesApiAdaptor)
    def accountCredentialsRepositoryMock = Mock(AccountCredentialsRepository)

    def credentials = new KubernetesCredentials(apiMock, NAMESPACES, [], accountCredentialsRepositoryMock)
    namedCredentialsMock.getName() >> VALID_CREDENTIALS
    namedCredentialsMock.getCredentials() >> credentials
    credentialsRepo.save(VALID_CREDENTIALS, namedCredentialsMock)
    validator.accountCredentialsProvider = credentialsProvider

    validPort = new KubernetesNamedServicePort(name: VALID_NAME, port: VALID_PORT, protocol: VALID_PROTOCOL)
    invalidNamePort = new KubernetesNamedServicePort(name: INVALID_NAME, protocol: VALID_PROTOCOL, port: VALID_PORT)
    invalidPortPort = new KubernetesNamedServicePort(name: VALID_NAME, port: INVALID_PORT, protocol: VALID_PROTOCOL)
    invalidProtocolPort = new KubernetesNamedServicePort(name: VALID_NAME, protocol: INVALID_PROTOCOL, port: VALID_PORT)
  }

  void "validation accept (all fields filled)"() {
    setup:
      def description = new UpsertKubernetesLoadBalancerAtomicOperationDescription(name: VALID_NAME,
        externalIps: [VALID_IP],
        ports: [validPort],
        credentials: VALID_CREDENTIALS,
        namespace: NAMESPACE)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)

    then:
      0 * errorsMock._
  }

  void "validation accept (some fields filled)"() {
    setup:
      def description = new UpsertKubernetesLoadBalancerAtomicOperationDescription(name: VALID_NAME,
        ports: [validPort],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)

    then:
      0 * errorsMock._
  }
  void "validation reject (bad protocol)"() {
    setup:
      def description = new UpsertKubernetesLoadBalancerAtomicOperationDescription(name: VALID_NAME,
        ports: [invalidProtocolPort],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)

    then:
      1 * errorsMock.rejectValue(_, "${DESCRIPTION}.ports[0].protocol.invalid (Must be one of $StandardKubernetesAttributeValidator.protocolList)")
  }

  void "validation reject (bad port name)"() {
    setup:
      def description = new UpsertKubernetesLoadBalancerAtomicOperationDescription(name: VALID_NAME,
        ports: [invalidNamePort],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)

    then:
      1 * errorsMock.rejectValue(_, "${DESCRIPTION}.ports[0].name.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
  }

  void "validation reject (bad port value)"() {
    setup:
      def description = new UpsertKubernetesLoadBalancerAtomicOperationDescription(name: VALID_NAME,
        ports: [invalidPortPort],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)

    then:
      1 * errorsMock.rejectValue(_, "${DESCRIPTION}.ports[0].port.invalid (Must be in range [1, $StandardKubernetesAttributeValidator.maxPort])")
  }

  void "validation reject (bad ip value)"() {
    setup:
      def description = new UpsertKubernetesLoadBalancerAtomicOperationDescription(name: VALID_NAME,
        ports: [validPort],
        externalIps: [INVALID_IP],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)

    then:
      1 * errorsMock.rejectValue(_, "${DESCRIPTION}.externalIps[0].invalid (Not valid IPv4 address)")
  }
}
