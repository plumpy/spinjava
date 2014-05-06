package com.netflix.kato.deploy.aws.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.kato.deploy.aws.description.CreateAmazonLoadBalancerDescription
import com.netflix.kato.deploy.aws.ops.loadbalancer.CreateAmazonLoadBalancerAtomicOperation
import com.netflix.kato.security.NamedAccountCredentials
import com.netflix.kato.security.NamedAccountCredentialsHolder
import spock.lang.Shared
import spock.lang.Specification

class CreateAmazonLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  CreateAmazonLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CreateAmazonLoadBalancerAtomicOperationConverter(objectMapper: new ObjectMapper())
    def namedAccountCredentialsHolder = Mock(NamedAccountCredentialsHolder)
    def mockCredentials = Mock(NamedAccountCredentials)
    namedAccountCredentialsHolder.getCredentials(_) >> mockCredentials
    converter.namedAccountCredentialsHolder = namedAccountCredentialsHolder
  }

  void "basicAmazonDeployDescription type returns BasicAmazonDeployDescription and DeployAtomicOperation"() {
    setup:
      def input = [clusterName: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]],
                   listeners:
                       [[externalProtocol: "HTTP", internalProtocol: "HTTP", externalPort: 7001, internalPort: 7001]],
                   credentials: "test"]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof CreateAmazonLoadBalancerDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof CreateAmazonLoadBalancerAtomicOperation
  }
}
