/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.templates

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription

class AzureLoadBalancerResourceTemplate {

  static ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)

  static String getTemplate(AzureLoadBalancerDescription description) {
    LoadBalancerTemplate template = new LoadBalancerTemplate(description)
    mapper.writeValueAsString(template)
  }

  static class LoadBalancerTemplate{
    String $schema = "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#"
    String contentVersion = "1.0.0.0"

    LoadBalancerParameters parameters
    LoadBalancerTemplateVariables variables
    ArrayList<Resource> resources = []

    LoadBalancerTemplate(AzureLoadBalancerDescription description){
      parameters = new LoadBalancerParameters()
      variables = new LoadBalancerTemplateVariables(description)

      resources.add(new PublicIpResource())

      LoadBalancer lb = new LoadBalancer(description)
      lb.addDependency(resources[0])
      resources.add(lb)
    }
  }

  static class LoadBalancerTemplateVariables{
    String loadBalancerName
    String virtualNetworkName
    String publicIPAddressName
    String publicIPAddressType = "Dynamic"
    String loadBalancerFrontEnd
    String loadBalancerBackEnd
    String dnsNameForLBIP
    String ipConfigName
    String loadBalancerID = "[resourceID('Microsoft.Network/loadBalancers',variables('loadBalancerName'))]"
    String publicIPAddressID = "[resourceID('Microsoft.Network/publicIPAddresses',variables('publicIPAddressName'))]"
    String frontEndIPConfig = "[concat(variables('loadBalancerID'),'/frontendIPConfigurations/',variables('loadBalancerFrontEnd'))]"
    String backendPoolID = "[concat(variables('loadBalancerID'),'/backendAddressPools/',variables('loadBalancerBackEnd'))]"

    LoadBalancerTemplateVariables(AzureLoadBalancerDescription description){
      String regionName = description.region.replace(' ', '').toLowerCase()
      String resourceGroupName = AzureUtilities.getResourceGroupName(description)

      loadBalancerName = description.loadBalancerName.toLowerCase()
      virtualNetworkName = AzureUtilities.VNET_NAME_PREFIX + resourceGroupName.toLowerCase()
      publicIPAddressName = AzureUtilities.PUBLICIP_NAME_PREFIX + description.loadBalancerName.toLowerCase()
      loadBalancerFrontEnd = AzureUtilities.LBFRONTEND_NAME_PREFIX + description.loadBalancerName.toLowerCase()
      loadBalancerBackEnd = AzureUtilities.LBBACKEND_NAME_PREFIX + description.loadBalancerName.toLowerCase()
      dnsNameForLBIP = AzureUtilities.DNS_NAME_PREFIX + description.loadBalancerName.toLowerCase()
      ipConfigName = AzureUtilities.IPCONFIG_NAME_PREFIX + description.loadBalancerName.toLowerCase()
    }
  }

  static class LoadBalancerParameters{
    Location location = new Location()
  }

  static class Location{
    String type = "string"
    Map<String, String> metadata = ["description":"Location to deploy"]
  }

  static class LoadBalancer extends DependingResource{
    LoadBalancerProperties properties

    LoadBalancer(AzureLoadBalancerDescription description) {
      apiVersion = "2015-05-01-preview"
      name = "[variables('loadBalancerName')]"
      type = "Microsoft.Network/loadBalancers"
      location = "[parameters('location')]"
      tags = [:]
      tags.appName = description.appName
      tags.stack = description.stack
      tags.detail = description.detail
      if (description.cluster) tags.cluster = description.cluster
      if (description.serverGroup) tags.serverGroup = description.serverGroup
      if (description.vnet) tags.vnet = description.vnet

      properties = new LoadBalancerProperties(description)
    }
  }

  private static class AzureProbe {
    AzureProbeProperty properties
    String name

    AzureProbe(AzureLoadBalancerDescription.AzureLoadBalancerProbe probe) {
      properties = new AzureProbeProperty(probe)
      name = probe.probeName
    }

    private static class AzureProbeProperty {
      String protocol
      Integer port
      Integer intervalInSeconds
      String requestPath
      Integer numberOfProbes

      AzureProbeProperty(AzureLoadBalancerDescription.AzureLoadBalancerProbe probe){
        protocol = probe.probeProtocol.toString().toLowerCase()
        port = probe.probePort
        intervalInSeconds = probe.probeInterval
        numberOfProbes = probe.unhealthyThreshold
        requestPath = probe.probePath
      }
    }
  }

  static class LoadBalancerProperties{
    ArrayList<FrontEndIpConfiguration> frontEndIPConfigurations = []
    ArrayList<BackEndAddressPool> backendAddressPools = []
    ArrayList<LoadBalancingRule> loadBalancingRules = []
    ArrayList<AzureProbe> probes = []

    LoadBalancerProperties(AzureLoadBalancerDescription description){
      frontEndIPConfigurations.add(new FrontEndIpConfiguration())
      backendAddressPools.add(new BackEndAddressPool())
      description.loadBalancingRules?.each{loadBalancingRules.add(new LoadBalancingRule(it))}
      description.probes?.each{ probes.add(new AzureProbe(it))}
    }
  }

  static class FrontEndIpConfiguration{
    String name
    FrontEndIpProperties properties

    FrontEndIpConfiguration()
    {
      name = "[variables('loadBalancerFrontEnd')]"
      properties = new FrontEndIpProperties("[variables('publicIPAddressID')]")
    }
  }

  static class BackEndAddressPool{
    String name

    BackEndAddressPool()
    {
      name = "[variables('loadBalancerBackEnd')]"
    }
  }

  static class FrontEndIpProperties{
    IdRef publicIPAddress

    FrontEndIpProperties(String id){
      publicIPAddress = new IdRef(id)
    }
  }

  static class Subnet{
    def name = '''[variables('subnetName')]'''
    def properties = new SubnetProperties()
  }

  static class SubnetProperties{
    def addressPrefix = '''[variables('subnetPrefix')]'''
  }

  static class PublicIpResource extends Resource{

    PublicIpResource() {
      apiVersion = '2015-05-01-preview'
      name = '''[variables('publicIPAddressName')]'''
      type = '''Microsoft.Network/publicIPAddresses'''
      location = '''[parameters('location')]'''
    }
    PublicIPProperties properties = new PublicIPProperties()
  }

  static class PublicIPProperties{
    String publicIPAllocationMethod = '''[variables('publicIPAddressType')]'''
    DnsSettings dnsSettings = new DnsSettings()
  }

  static class DnsSettings{
    String domainNameLabel = '''[variables('dnsNameForLBIP')]'''
  }

  static class NetworkInterfaceProperties{
    ArrayList<IPConfiguration> ipConfigurations = []

    public NetworkInterfaceProperties(){
      ipConfigurations.add( new IPConfiguration())
    }
  }

  static class IPConfiguration{
    String name = '''[variables('ipConfigName')]'''
    IPConfigurationProperties properties = new IPConfigurationProperties()
    ArrayList<IdRef> loadBalancerBackendAddressPools = []

    public IPConfiguration()
    {
      loadBalancerBackendAddressPools.add(new IdRef('''[concat(resourceId('Microsoft.Network/loadBalancers', variables('loadBalancerName')),'/backendAddressPools/loadBalancerBackEnd')]'''))
    }
  }

  static class IPConfigurationProperties{
    String privateIPAllocationMethod = '''Dynamic'''
    IdRef subnet = new IdRef('''[variables('subnetRefID')]''')
  }

  static class IdRef{
    String id

    public IdRef(String refID)
    {
      id = refID
    }
  }

  static class LoadBalancingRule{
    String name
    LoadBalancingRuleProperties properties

    LoadBalancingRule(AzureLoadBalancerDescription.AzureLoadBalancingRule rule){
      name = rule.ruleName
      properties = new LoadBalancingRuleProperties(rule)
    }

  }

  static class LoadBalancingRuleProperties{
    IdRef frontendIPConfiguration
    IdRef backendAddressPool
    String protocol
    Integer frontendPort
    Integer backendPort
    IdRef probe

    LoadBalancingRuleProperties(AzureLoadBalancerDescription.AzureLoadBalancingRule rule){
      frontendIPConfiguration = new IdRef("[variables('frontEndIPConfig')]")
      backendAddressPool = new IdRef("[variables('backendPoolID')]")
      protocol = rule.protocol.toString().toLowerCase()
      frontendPort = rule.externalPort
      backendPort = rule.backendPort
      probe = new IdRef("[concat(variables('loadBalancerID'),'/probes/" + rule.probeName + "')]")
    }
  }
}
