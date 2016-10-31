/*
 * Copyright 2016 The original authors.
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
/*
 * Copyright 2016 The original authors.
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
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription.AzureInboundPortConfig
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription

@Slf4j
class AzureServerGroupResourceTemplate {
  static final String STORAGE_ACCOUNT_SUFFIX = "sa"

  static String LB_NAME = null

  protected static ObjectMapper mapper = new ObjectMapper()
    .configure(SerializationFeature.INDENT_OUTPUT, true)
    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

  /**
   * Build the resource manager template that will create the Azure equivalent (VM Scale Set)
   * of the Spinnaker Server Group
   * @param description - Description object containing the values to be specified in the template
   * @return - JSON string representing the Resource Manager template for a Azure VM Scale Set (Server Group)
   */
  static String getTemplate(AzureServerGroupDescription description) {
    ServerGroupTemplate template = new ServerGroupTemplate(description)
    mapper.writeValueAsString(template)
  }

  /**
   * Initialize variables that will be used in mulitple places
   * @param description Azure Server Group description object
     */
  private static void initializeCommonVariables(AzureServerGroupDescription description) {
    LB_NAME = AzureUtilities.LB_NAME_PREFIX + description.name
  }

  /**
   *
   */
  static class ServerGroupTemplate {
    String $schema = "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#"
    String contentVersion = "1.0.0.0"

    ServerGroupTemplateParameters parameters
    TemplateVariables variables
    ArrayList<Resource> resources = []

    /**
     *
     * @param description
     */
    ServerGroupTemplate(AzureServerGroupDescription description) {
      initializeCommonVariables(description)
      parameters = new ServerGroupTemplateParameters()

      //If it's custom,
      if (description.image.isCustom) {
        variables = new CoreServerGroupTemplateVariables(description)
      } else {
        variables = new ExtendedServerGroupTemplateVariables(description)
        resources.add(new StorageAccount(description))
      }

      resources.add(new PublicIpResource(properties: new PublicIPPropertiesWithDns()))
      resources.add(new LoadBalancer(description))
      resources.add(new VirtualMachineScaleSet(description))
    }

  }

  interface TemplateVariables {}

  static class CoreServerGroupTemplateVariables implements TemplateVariables {
    final String apiVersion = "2015-06-15"
    String publicIPAddressName
    String publicIPAddressID
    String publicIPAddressType
    String dnsNameForLBIP
    String loadBalancerBackend
    String loadBalancerFrontEnd
    String loadBalancerName
    String loadBalancerID
    String frontEndIPConfigID
    String inboundNatPoolName

    CoreServerGroupTemplateVariables() {}

    CoreServerGroupTemplateVariables(AzureServerGroupDescription description) {
      publicIPAddressName = AzureUtilities.PUBLICIP_NAME_PREFIX + description.name
      publicIPAddressID = "[resourceId('Microsoft.Network/publicIPAddresses', variables('publicIPAddressName'))]"
      publicIPAddressType = "Dynamic"
      dnsNameForLBIP = AzureUtilities.DNS_NAME_PREFIX + description.name.toLowerCase()
      frontEndIPConfigID = "[resourceId('Microsoft.Network/loadBalancers/frontendIPConfigurations/', variables('loadBalancerName'), variables('loadBalancerFrontEnd'))]"
      loadBalancerFrontEnd = AzureUtilities.LBFRONTEND_NAME_PREFIX + description.name
      loadBalancerBackend = AzureUtilities.LBBACKEND_NAME_PREFIX + description.name
      loadBalancerName = LB_NAME
      loadBalancerID = "[resourceId('Microsoft.Network/loadBalancers', variables('loadBalancerName'))]"
      inboundNatPoolName = AzureUtilities.INBOUND_NATPOOL_PREFIX + description.name
    }
  }

  /**
   *
   */
  static class ExtendedServerGroupTemplateVariables extends CoreServerGroupTemplateVariables implements TemplateVariables {
    // The values of these variables need to match the name of the corresponding variables below
    // The reason is that in other sections of the RM template (i.e., Resources) their is a reference by name
    // to the variable defined in Variables section of the template.
    // These *Var variables are meant to help keep that reference from breaking IF the name of the actual variable
    // changes
    static transient String uniqueStorageNamesArrayVar = 'uniqueStorageNameArray'
    static transient String vhdContainerNameVar = 'vhdContainerName'

    String vhdContainerName
    OsType osType
    String imageReference
    ArrayList<String> uniqueStorageNameArray = []

    /**
     *
     * @param description
     */
    ExtendedServerGroupTemplateVariables(AzureServerGroupDescription description) {
      super(description)
      vhdContainerName = description.name.toLowerCase()
      osType = new OsType(description)
      imageReference = "[variables('osType')]"

      for (int i = 0; i < description.getStorageAccountCount(); i++) {
        uniqueStorageNameArray.add(getUniqueStorageName(description.name, i))
      }
    }
  }

  static String getUniqueStorageName(String name, long idx) {
    String noDashName = name.replaceAll("-", "").toLowerCase()
    "[concat(uniqueString(concat(resourceGroup().id, subscription().id, '$noDashName', '$idx')), '$STORAGE_ACCOUNT_SUFFIX')]"
  }

  /**
   *
   */
  static class ServerGroupTemplateParameters {
    LocationParameter location = new LocationParameter(["description": "Location to deploy"])
    SubnetParameter subnetId = new SubnetParameter(["description": "Subnet Resource ID"])
    AppGatewayAddressPoolParameter appGatewayAddressPoolId = new AppGatewayAddressPoolParameter(["description": "App Gateway backend address pool resource ID"])
    VMUserNameParameter vmuserName = new VMUserNameParameter(["description": "default VM account name"])
    VMPasswordParameter vmPassword = new VMPasswordParameter(["description": "default VM account password"])
    CustomDataParameter customData = new CustomDataParameter(["description":"custom data to pass down to the virtual machine(s)"], "")
  }

  /* Server Group Parameters */
  static String subnetParameterName = "subnetId"
  static class SubnetParameter extends StringParameter {
    SubnetParameter(Map<String, String> metadata) {
      super(metadata)
    }
  }

  static String locationParameterName = "location"
  static class LocationParameter extends StringParameter {
    LocationParameter(Map<String, String> metadata) {
      super(metadata)
    }
  }

  static String appGatewayAddressPoolParameterName = "appGatewayAddressPoolId"
  static class AppGatewayAddressPoolParameter extends StringParameter {
    AppGatewayAddressPoolParameter(Map<String, String> metadata) {
      super(metadata)
    }
  }

  static String customDataParameterName = "customData"
  static class CustomDataParameter extends StringParameterWithDefault {
    CustomDataParameter(Map<String, String> metadata, String defValue) {
      super(metadata, defValue)
    }
  }

  static String vmUserNameParameterName = "vmUsername"
  static class VMUserNameParameter extends StringParameter {
    VMUserNameParameter(Map<String, String> metadata) {
      super(metadata)
    }
  }

  static String vmPasswordParameterName = "vmPassword"
  static class VMPasswordParameter extends SecureStringParameter {
    VMPasswordParameter(Map<String, String> metadata) {
      super(metadata)
    }
  }

  /**
   *
   */
  static class OsType {
    String publisher
    String offer
    String sku
    String version

    /**
     *
     * @param description
     */
    OsType(AzureServerGroupDescription description) {
      publisher = description.image.publisher
      offer = description.image.offer
      sku = description.image.sku
      version = description.image.version
    }
  }

  /**
   *
   */
  static class StorageAccount extends Resource {
    CopyOperation copy
    StorageAccountProperties properties

    /**
     *
     * @param description
     */
    StorageAccount(AzureServerGroupDescription description) {
      apiVersion = "[variables('apiVersion')]"
      name = String.format("[concat(variables('%s')[copyIndex()])]", ExtendedServerGroupTemplateVariables.uniqueStorageNamesArrayVar)
      type = "Microsoft.Storage/storageAccounts"
      location = "[parameters('${locationParameterName}')]"
      def currentTime = System.currentTimeMillis()

      copy = new CopyOperation("storageLoop", description.getStorageAccountCount())
      tags = [:]
      tags.appName = description.application
      tags.stack = description.stack
      tags.detail = description.detail
      tags.cluster = description.clusterName
      tags.serverGroupName = description.name
      tags.createdTime = currentTime.toString()

      properties = new StorageAccountProperties()
    }
  }

  /**
   *
   */
  static class StorageAccountProperties {
    String accountType

    /**
     *
     * @param description
     */
    StorageAccountProperties() {
      // Hardcoded value to use premium storage for better perf per Azure recommendation;
      // TODO: we will revisit this later given that premium storage is a bit more expensive than standard
      accountType = "Premium_LRS"
    }
  }

  /**
   *
   */
  static class CopyOperation {
    String name
    int count

    /**
     *
     * @param operationName
     * @param iterations
     */
    CopyOperation(String operationName, int iterations) {
      name = operationName
      count = iterations
    }
  }

  /**
   *
   */
  static class VirtualMachineScaleSet extends DependingResource {
    ScaleSetSkuProperty sku
    VirtualMachineScaleSetProperty properties

    VirtualMachineScaleSet(AzureServerGroupDescription description) {
      apiVersion = "[variables('apiVersion')]"
      name = description.name
      type = "Microsoft.Compute/virtualMachineScaleSets"
      location = "[parameters('${locationParameterName}')]"
      def currentTime = System.currentTimeMillis()
      tags = [:]
      tags.appName = description.application
      tags.stack = description.stack
      tags.detail = description.detail
      tags.cluster = description.clusterName
      tags.createdTime = currentTime.toString()
      tags.loadBalancerName = LB_NAME
      tags.hasNewSubnet = description.hasNewSubnet.toString()

      // debug only; can be removed as part of the tags cleanup
      if (description.appGatewayName) tags.appGatewayName = description.appGatewayName
      if (description.appGatewayBapId) tags.appGatewayBapId = description.appGatewayBapId

      if (description.securityGroupName) tags.securityGroupName = description.securityGroupName
      if (description.subnetId) tags.subnetId = description.subnetId
      tags.imageIsCustom = description.image.isCustom.toString()
      // will need this when cloning a server group
      if (description.image.imageName) tags.imageName = description.image.imageName

      if (!description.image.isCustom) {
        description.getStorageAccountCount().times { idx ->
          this.dependsOn.add(
            String.format("[concat('Microsoft.Storage/storageAccounts/', variables('%s')[%s])]",
              ExtendedServerGroupTemplateVariables.uniqueStorageNamesArrayVar,
              idx)
          )
          String uniqueName = getUniqueStorageName(description.name, idx)
          tags.storageAccountNames = tags.storageAccountNames ? "${tags.storageAccountNames},${uniqueName}" : uniqueName
        }
      }

      this.dependsOn.add("[concat('Microsoft.Network/loadBalancers/', variables('loadBalancerName'))]")

      properties = new VirtualMachineScaleSetProperty(description)
      sku = new ScaleSetSkuProperty(description)
    }
  }

  static class VirtualMachineScaleSetProperty {
    Map<String, String> upgradePolicy = [:]
    ScaleSetVMProfile virtualMachineProfile

    VirtualMachineScaleSetProperty(AzureServerGroupDescription description) {
      upgradePolicy["mode"] = description.upgradePolicy.toString()
      virtualMachineProfile = description.customScriptSettings?.fileUris ?
        new ScaleSetVMProfilePropertyWithExtension(description) :
        new ScaleSetVMProfileProperty(description)


    }
  }

  // ***Scale Set SKU
  /**
   *
   */
  static class ScaleSetSkuProperty {
    String name
    String tier
    int capacity

    /**
     *
     * @param description
     */
    ScaleSetSkuProperty(AzureServerGroupDescription description) {
      name = description.sku.name
      tier = description.sku.tier
      capacity = description.sku.capacity
    }
  }

  // ***OSProfile
  static class ScaleSetOsProfileProperty {
    String computerNamePrefix
    String adminUsername
    String adminPassword
    String customData

    /**
     *
     * @param description
     */
    ScaleSetOsProfileProperty(AzureServerGroupDescription description) {
      //Max length of 10 characters to allow for an aditional postfix within a max length of 15 characters
      computerNamePrefix = description.getIdentifier().substring(0, 10)
      log.info("computerNamePrefix will be truncated to 10 characters to maintain Azure restrictions")
      adminUsername = "[parameters('${vmUserNameParameterName}')]"
      adminPassword = "[parameters('${vmPasswordParameterName}')]"
      customData = "[base64(parameters('customData'))]"
    }
  }


  // ***Network Profile
  /**
   *
   */
  static class ScaleSetNetworkProfileProperty {
    ArrayList<NetworkInterfaceConfiguration> networkInterfaceConfigurations = []

    /**
     *
     * @param description
     */
    ScaleSetNetworkProfileProperty(AzureServerGroupDescription description) {
      networkInterfaceConfigurations.add(new NetworkInterfaceConfiguration(description))
    }
  }

  /**
   *
   */
  static class NetworkInterfaceConfiguration {
    String name
    NetworkInterfaceConfigurationProperty properties

    /**
     *
     * @param description
     */
    NetworkInterfaceConfiguration(AzureServerGroupDescription description) {
      name = AzureUtilities.NETWORK_INTERFACE_PREFIX + description.getIdentifier()
      properties = new NetworkInterfaceConfigurationProperty(description)
    }
  }

  /**
   *
   */
  static class NetworkInterfaceConfigurationProperty {
    boolean primary
    ArrayList<NetworkInterfaceIPConfiguration> ipConfigurations = []

    /**
     *
     * @param description
     */
    NetworkInterfaceConfigurationProperty(AzureServerGroupDescription description) {
      primary = true
      ipConfigurations.add(new NetworkInterfaceIPConfiguration(description))
    }
  }

  /**
   *
   */
  static class NetworkInterfaceIPConfiguration {
    String name
    NetworkInterfaceIPConfigurationsProperty properties

    /**
     *
     * @param description
     */
    NetworkInterfaceIPConfiguration(AzureServerGroupDescription description) {
      name = AzureUtilities.IPCONFIG_NAME_PREFIX + description.getIdentifier()
      properties = new NetworkInterfaceIPConfigurationsProperty()
    }
  }

  /**
   *
   */
  static class NetworkInterfaceIPConfigurationsProperty {
    NetworkInterfaceIPConfigurationSubnet subnet
    ArrayList<AppGatewayBackendAddressPool> ApplicationGatewayBackendAddressPools = []
    ArrayList<LoadBalancerBackendAddressPool> loadBalancerBackendAddressPools = []
    ArrayList<LoadBalancerInboundNatPoolId> loadBalancerInboundNatPools = []

    /**
     *
     * @param description
     */
    NetworkInterfaceIPConfigurationsProperty() {
      subnet = new NetworkInterfaceIPConfigurationSubnet()
      loadBalancerBackendAddressPools.add(new LoadBalancerBackendAddressPool())
      ApplicationGatewayBackendAddressPools.add(new AppGatewayBackendAddressPool())
      loadBalancerInboundNatPools.add(new LoadBalancerInboundNatPoolId())
    }
  }

  /**
   *
   */
  static class NetworkInterfaceIPConfigurationSubnet {
    String id

    NetworkInterfaceIPConfigurationSubnet() {
      id = "[parameters('${subnetParameterName}')]"
    }
  }

  static class LoadBalancerBackendAddressPool {
    String id

    LoadBalancerBackendAddressPool() {
      id = "[resourceId('Microsoft.Network/loadBalancers/backendAddressPools', variables('loadBalancerName'), variables('loadBalancerBackend'))]"
    }
  }

  static class LoadBalancerInboundNatPoolId extends IdRef {

    LoadBalancerInboundNatPoolId() {
      id = "[resourceId('Microsoft.Network/loadBalancers/inboundNatPools', variables('loadBalancerName'), variables('inboundNatPoolName'))]"
    }
  }

  static class AppGatewayBackendAddressPool {
    String id

    AppGatewayBackendAddressPool() {
      id = "[parameters('${appGatewayAddressPoolParameterName}')]"
    }
  }

  interface ScaleSetVMProfile {}
  // ***VM Profile
  /**
   *
   */
  static class ScaleSetVMProfileProperty implements ScaleSetVMProfile {
    StorageProfile storageProfile
    ScaleSetOsProfileProperty osProfile
    ScaleSetNetworkProfileProperty networkProfile

    ScaleSetVMProfileProperty(AzureServerGroupDescription description) {
      storageProfile = description.image.isCustom ?
        new ScaleSetCustomImageStorageProfile(description) :
        new ScaleSetStorageProfile(description)
      osProfile = new ScaleSetOsProfileProperty(description)
      networkProfile = new ScaleSetNetworkProfileProperty(description)

    }
  }

  static class ScaleSetVMProfilePropertyWithExtension extends ScaleSetVMProfileProperty implements ScaleSetVMProfile {
    ScaleSetExtensionProfileProperty extensionProfile
    ScaleSetVMProfilePropertyWithExtension(AzureServerGroupDescription description) {
      super(description)
      extensionProfile = new ScaleSetExtensionProfileProperty(description)
    }
  }

  interface OSDisk {
    String name
    String caching
    String createOption
  }

  interface StorageProfile {
    OSDisk osDisk
  }

  /**
   *
   */
  static class ScaleSetStorageProfile implements StorageProfile {

    OSDisk osDisk
    String imageReference
    /**
     *
     * @param serverGroupDescription
     */
    ScaleSetStorageProfile(AzureServerGroupDescription description) {
      osDisk = new VirtualMachineOSDisk(description)
      imageReference = "[variables('imageReference')]"
    }
  }

  /**
   *
   */
  static class ScaleSetCustomImageStorageProfile implements StorageProfile {

    OSDisk osDisk
    /**
     *
     * @param serverGroupDescription
     */
    ScaleSetCustomImageStorageProfile(AzureServerGroupDescription description) {
      osDisk = new VirtualMachineCustomImageOSDisk(description)
    }
  }

  static class VirtualMachineOSDisk implements OSDisk {

    String name
    String caching
    String createOption
    ArrayList<String> vhdContainers = []

    VirtualMachineOSDisk(AzureServerGroupDescription description) {
      name = "osdisk-" + description.name
      caching = "ReadOnly"
      createOption = "FromImage"
      description.getStorageAccountCount().times { idx ->
        vhdContainers.add(String.format("[concat('https://', variables('%s')[%s], '.blob.core.windows.net/', variables('%s'))]",
          ExtendedServerGroupTemplateVariables.uniqueStorageNamesArrayVar,
          idx,
          ExtendedServerGroupTemplateVariables.vhdContainerNameVar))
      }
    }
  }

  static class VirtualMachineCustomImageOSDisk implements OSDisk {

    String name
    String caching
    String createOption
    String osType
    Map<String, String> image = [:]

    VirtualMachineCustomImageOSDisk(AzureServerGroupDescription description) {
      name = "osdisk-${description.name}"
      caching = "ReadOnly"
      createOption = "FromImage"
      osType = description.image.ostype
      image.uri = description.image.uri
    }
  }


  /**** VMSS extensionsProfile ****/
  static class ScaleSetExtensionProfileProperty {
    Collection<Extensions> extensions = []

    ScaleSetExtensionProfileProperty(AzureServerGroupDescription description) {
      extensions.add(new Extensions(description))
    }
  }

  static class Extensions {
    String name
    ExtensionProperty properties

    Extensions(AzureServerGroupDescription description) {
      name = description.application + "_ext"
      properties = new ExtensionProperty(description)
    }
  }

  static class ExtensionProperty {
    String publisher
    String type
    String typeHandlerVersion // This will need to be updated every time the custom script extension major version is updated
    Boolean autoUpgradeMinorVersion = true
    CustomScriptExtensionSettings settings

    ExtensionProperty(AzureServerGroupDescription description) {
      settings = new CustomScriptExtensionSettings(description)
      publisher = description.image?.ostype?.toLowerCase() == "linux" ? AzureUtilities.AZURE_CUSTOM_SCRIPT_EXT_PUBLISHER_LINUX : AzureUtilities.AZURE_CUSTOM_SCRIPT_EXT_PUBLISHER_WINDOWS
      type = description.image?.ostype?.toLowerCase() == "linux" ? AzureUtilities.AZURE_CUSTOM_SCRIPT_EXT_TYPE_LINUX: AzureUtilities.AZURE_CUSTOM_SCRIPT_EXT_TYPE_WINDOWS
      typeHandlerVersion = description.image?.ostype?.toLowerCase() == "linux" ? AzureUtilities.AZURE_CUSTOM_SCRIPT_EXT_VERSION_LINUX : AzureUtilities.AZURE_CUSTOM_SCRIPT_EXT_VERSION_WINDOWS
    }
  }

  static class CustomScriptExtensionSettings {
    Collection<String> fileUris
    String commandToExecute

    CustomScriptExtensionSettings(AzureServerGroupDescription description) {
      commandToExecute = description.customScriptSettings.commandToExecute
      fileUris = description.customScriptSettings.fileUris
    }
  }


  /**** Load Balancer Resource ****/
  static class LoadBalancer extends DependingResource {
    LoadBalancerProperties properties

    LoadBalancer(AzureServerGroupDescription description) {
      apiVersion = "[variables('apiVersion')]"
      name = "[variables('loadBalancerName')]"
      type = "Microsoft.Network/loadBalancers"
      location = "[parameters('${locationParameterName}')]"
      def currentTime = System.currentTimeMillis()
      tags = [:]
      tags.appName = description.application
      tags.stack = description.stack
      tags.detail = description.detail
      tags.createdTime = currentTime.toString()
      if (description.clusterName) tags.cluster = description.clusterName
      if (description.name) tags.serverGroup = description.name
      if (description.securityGroupName) tags.securityGroupName = description.securityGroupName

      this.dependsOn.add("[concat('Microsoft.Network/publicIPAddresses/', variables('publicIPAddressName'))]")

      properties = new LoadBalancerProperties(description)
    }
  }

  static class LoadBalancerProperties {
    ArrayList<FrontEndIpConfiguration> frontendIPConfigurations = []
    ArrayList<BackEndAddressPool> backendAddressPools = []
    ArrayList<InboundNatPool> inboundNatPools = []

    LoadBalancerProperties(AzureServerGroupDescription description) {
      frontendIPConfigurations.add(new FrontEndIpConfiguration())
      backendAddressPools.add(new BackEndAddressPool())
      description.inboundPortConfigs?.each {
        inboundNatPools.add(new InboundNatPool(it))
      }
    }
  }

  static class FrontEndIpConfiguration {
    String name
    FrontEndIpProperties properties

    FrontEndIpConfiguration() {
      name = "[variables('loadBalancerFrontEnd')]"
      properties = new FrontEndIpProperties("[variables('publicIPAddressID')]")
    }
  }

  static class FrontEndIpProperties {
    IdRef publicIpAddress

    FrontEndIpProperties(String id) {
      publicIpAddress = new IdRef(id)
    }
  }

  static class BackEndAddressPool {
    String name

    BackEndAddressPool() {
      name = "[variables('loadBalancerBackEnd')]"
    }
  }


  static class InboundNatPool {
    String name
    InboundNatPoolProperties properties

    InboundNatPool(AzureInboundPortConfig inboundPortConfig) {
      name = inboundPortConfig.name
      properties = new InboundNatPoolProperties(inboundPortConfig)
    }
  }

  static class InboundNatPoolProperties {
    IdRef frontendIPConfiguration
    String protocol
    int frontendPortRangeStart
    int frontendPortRangeEnd
    int backendPort

    InboundNatPoolProperties(AzureInboundPortConfig inboundPortConfig) {
      frontendIPConfiguration = new IdRef("[variables('frontEndIPConfigID')]")
      protocol = inboundPortConfig.protocol
      frontendPortRangeStart = inboundPortConfig.frontEndPortRangeStart
      frontendPortRangeEnd = inboundPortConfig.frontEndPortRangeEnd
      backendPort = inboundPortConfig.backendPort
    }
  }
/*
  static class PublicIpResource extends Resource {

    PublicIpResource() {
      apiVersion = '2015-06-15'
      name = '''[variables('publicIpAddressName')]'''
      type = '''Microsoft.Network/publicIPAddresses'''
      location = "[parameters('${locationParameterName}')]"
    }
    PublicIPPropertiesWithDns properties = new PublicIPPropertiesWithDns()
  }

  static class PublicIPProperties {
    String publicIPAllocationMethod = '''[variables('publicIpAddressType')]'''
    DnsSettings dnsSettings = new DnsSettings()
  }

  static class DnsSettings {
    String domainNameLabel = '''[variables('dnsNameForLBIP')]'''
  }
*/
}
