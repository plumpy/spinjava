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

package com.netflix.spinnaker.kato.gce.deploy

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.frigga.NameValidation
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.kato.config.GceConfig
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.gce.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.kato.gce.deploy.description.UpsertGoogleSecurityGroupDescription
import com.netflix.spinnaker.kato.gce.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.kato.gce.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.kato.gce.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.mort.gce.model.GoogleSecurityGroup
import com.netflix.spinnaker.mort.gce.provider.view.GoogleSecurityGroupProvider

class GCEUtil {
  private static final String DISK_TYPE_PERSISTENT = "PERSISTENT"

  public static final String TARGET_POOL_NAME_PREFIX = "tp"

  // TODO(duftler): This list should not be static, but should also not be built on each call.
  static final List<String> baseImageProjects = ["centos-cloud", "coreos-cloud", "debian-cloud", "google-containers",
                                                 "opensuse-cloud", "rhel-cloud", "suse-cloud", "ubuntu-os-cloud"]

  static MachineType queryMachineType(String projectName, String zone, String machineTypeName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up machine type $machineTypeName..."
    def machineType = compute.machineTypes().list(projectName, zone).execute().getItems().find {
      it.getName() == machineTypeName
    }

    if (machineType) {
      return machineType
    } else {
      updateStatusAndThrowNotFoundException("Machine type $machineTypeName not found.", task, phase)
    }
  }

  static Image querySourceImage(String projectName,
                                String sourceImageName,
                                Compute compute,
                                Task task,
                                String phase,
                                String googleApplicationName) {
    task.updateStatus phase, "Looking up source image $sourceImageName..."

    def imageProjects = [projectName] + baseImageProjects
    def sourceImage = null

    def imageListBatch = buildBatchRequest(compute, googleApplicationName)
    def imageListCallback = new JsonBatchCallback<ImageList>() {
      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        updateStatusAndThrowNotFoundException("Error locating $sourceImageName in these projects: $imageProjects: $e.message", task, phase)
      }

      @Override
      void onSuccess(ImageList imageList, HttpHeaders responseHeaders) throws IOException {
        for (def image : imageList.items) {
          if (image.name == sourceImageName) {
            sourceImage = image
          }
        }
      }
    }

    for (imageProject in imageProjects) {
      compute.images().list(imageProject).queue(imageListBatch, imageListCallback)
    }

    imageListBatch.execute()

    if (sourceImage) {
      return sourceImage
    } else {
      updateStatusAndThrowNotFoundException("Source image $sourceImageName not found in any of these projects: $imageProjects.", task, phase)
    }
  }

  private static BatchRequest buildBatchRequest(def compute, def googleApplicationName) {
    return compute.batch(
      new HttpRequestInitializer() {
        @Override
        void initialize(HttpRequest request) throws IOException {
          request.headers.setUserAgent(googleApplicationName);
        }
      }
    )
  }

  static Network queryNetwork(String projectName, String networkName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up network $networkName..."
    def network = compute.networks().list(projectName).execute().getItems().find {
      it.getName() == networkName
    }

    if (network) {
      return network
    } else {
      updateStatusAndThrowNotFoundException("Network $networkName not found.", task, phase)
    }
  }

  // If a forwarding rule with the specified name is found in any region, it is returned.
  static ForwardingRule queryRegionalForwardingRule(
    String projectName, String forwardingRuleName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Checking for existing network load balancer (forwarding rule) $forwardingRuleName..."

    // Try to retrieve this forwarding rule in each region.
    for (def region : compute.regions().list(projectName).execute().items) {
      try {
        return compute.forwardingRules().get(projectName, region.name, forwardingRuleName).execute()
      } catch (GoogleJsonResponseException e) {
        // 404 is thrown if the forwarding rule does not exist in the given region. Any other exception needs to be propagated.
        if (e.getStatusCode() != 404) {
          throw e
        }
      }
    }
  }

  static TargetPool queryTargetPool(
    String projectName, String region, String targetPoolName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Checking for existing network load balancer (target pool) $targetPoolName..."

    return compute.targetPools().list(projectName, region).execute().items.find { existingTargetPool ->
      existingTargetPool.name == targetPoolName
    }
  }

  static HttpHealthCheck queryHttpHealthCheck(
    String projectName, String httpHealthCheckName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Checking for existing network load balancer (http health check) $httpHealthCheckName..."

    return compute.httpHealthChecks().list(projectName).execute().items.find { existingHealthCheck ->
      existingHealthCheck.name == httpHealthCheckName
    }
  }

  static List<ForwardingRule> queryForwardingRules(
          String projectName, String region, List<String> forwardingRuleNames, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up network load balancers $forwardingRuleNames..."

    def foundForwardingRules = compute.forwardingRules().list(projectName, region).execute().items.findAll {
      it.name in forwardingRuleNames
    }

    if (foundForwardingRules.size == forwardingRuleNames.size) {
      return foundForwardingRules
    } else {
      def foundNames = foundForwardingRules.collect { it.name }

      updateStatusAndThrowNotFoundException("Network load balancers ${forwardingRuleNames - foundNames} not found.", task, phase)
    }
  }

  static List<String> queryInstanceUrls(String projectName,
                                        String region,
                                        List<String> instanceLocalNames,
                                        Compute compute,
                                        Task task,
                                        String phase) {
    task.updateStatus phase, "Looking up instances $instanceLocalNames..."

    Map<String, InstancesScopedList> zoneToInstancesMap = compute.instances().aggregatedList(projectName).execute().items

    // Build up a list of all instances in the specified region with a name specified  in instanceLocalNames:
    //   1) Build a list of lists where each sublist represents the matching instances in one zone.
    //   2) Flatten the list of lists into a one-level list.
    //   3) Remove any null entries (null entries are possible because .collect() still accumulates an element even if
    //      the conditional evaluates to false; it's just a null element).
    def foundInstances = zoneToInstancesMap.collect { zone, instanceList ->
      if (zone.startsWith("zones/$region-") && instanceList.instances) {
        return instanceList.instances.findAll { instance ->
          return instanceLocalNames.contains(instance.name)
        }
      }
    }.flatten() - null

    if (foundInstances.size == instanceLocalNames.size) {
      return foundInstances.collect { it.selfLink }
    } else {
      def foundNames = foundInstances.collect { it.name }

      updateStatusAndThrowNotFoundException("Instances ${instanceLocalNames - foundNames} not found.", task, phase)
    }
  }

  static InstanceTemplate queryInstanceTemplate(String projectName, String instanceTemplateName, Compute compute) {
    compute.instanceTemplates().get(projectName, instanceTemplateName).execute()
  }

  static InstanceGroupManager queryManagedInstanceGroup(String projectName,
                                                        String zone,
                                                        String serverGroupName,
                                                        GoogleCredentials credentials) {
    credentials.compute.instanceGroupManagers().get(projectName, zone, serverGroupName).execute()
  }

  static List<InstanceGroupManager> queryManagedInstanceGroups(String projectName,
                                                               String region,
                                                               GoogleCredentials credentials) {
    def compute = credentials.compute
    def zones = getZonesFromRegion(projectName, region, compute)

    def allMIGSInRegion = zones.findResults {
      def localZoneName = getLocalName(it)

      compute.instanceGroupManagers().list(projectName, localZoneName).execute().getItems()
    }.flatten()

    allMIGSInRegion
  }

  static Set<String> querySecurityGroupTags(Set<String> securityGroupNames,
                                            String accountName,
                                            GoogleSecurityGroupProvider googleSecurityGroupProvider,
                                            Task task,
                                            String phase) {
    if (!securityGroupNames) {
      return null
    }

    task.updateStatus phase, "Looking up firewall rules $securityGroupNames..."

    Set<GoogleSecurityGroup> securityGroups = googleSecurityGroupProvider.getAllByAccount(false, accountName)

    Set<GoogleSecurityGroup> securityGroupMatches = securityGroups.findAll { securityGroup ->
      securityGroupNames.contains(securityGroup.name)
    }

    if (securityGroupMatches.size() == securityGroupNames.size()) {
      return securityGroupMatches.collect { securityGroupMatch ->
        securityGroupMatch.targetTags
      }.flatten() - null
    } else {
      def securityGroupNameMatches = securityGroupMatches.collect { it.name }

      updateStatusAndThrowNotFoundException("Firewall rules ${securityGroupNames - securityGroupNameMatches} not found.", task, phase)
    }

    return securityGroups.findAll { securityGroup ->
      securityGroupNames.contains(securityGroup.name)
    }.collect { securityGroup ->
      securityGroup.targetTags
    }.flatten() - null
  }

  static List<String> deriveInstanceUrls(String project,
                                         String zone,
                                         String managedInstanceGroupName,
                                         List<String> instanceIds,
                                         GoogleCredentials credentials) {
    def managedInstanceGroup = GCEUtil.queryManagedInstanceGroup(project, zone, managedInstanceGroupName, credentials)
    def baseUrl = managedInstanceGroup.selfLink.substring(0,
        managedInstanceGroup.getSelfLink().lastIndexOf("/instanceGroupManagers/${managedInstanceGroupName}"))

    instanceIds.collect { instanceId -> "$baseUrl/instances/$instanceId".toString() }
  }

  static List<String> mergeDescriptionAndSecurityGroupTags(List<String> tags, Set<String> securityGroupTags) {
    return ((tags ?: []) + securityGroupTags).unique()
  }

  static String getRegionFromZone(String projectName, String zone, Compute compute) {
    // Zone.getRegion() returns a full URL reference.
    def fullRegion = compute.zones().get(projectName, zone).execute().getRegion()
    // Even if getRegion() is changed to return just the unqualified region name, this will still work.
    getLocalName(fullRegion)
  }

  static List<String> getZonesFromRegion(String projectName, String region, Compute compute) {
    return compute.regions().get(projectName, region).execute().getZones()
  }

  static BaseGoogleInstanceDescription buildInstanceDescriptionFromTemplate(InstanceTemplate instanceTemplate) {
    def instanceTemplateProperties = instanceTemplate?.properties

    if (instanceTemplateProperties == null) {
      throw new GoogleOperationException("Unable to determine properties of instance template " +
          "$instanceTemplate.name.")
    }

    if (instanceTemplateProperties.networkInterfaces?.size != 1) {
      throw new GoogleOperationException("Instance templates must have exactly one network interface defined. " +
          "Instance template $instanceTemplate.name has ${instanceTemplateProperties.networkInterfaces?.size}.")
    }

    if (instanceTemplateProperties.disks?.size != 1) {
      throw new GoogleOperationException("Instance templates must have exactly one disk defined. Instance template " +
          "$instanceTemplate.name has ${instanceTemplateProperties.disks?.size}.")
    }

    def networkInterface = instanceTemplateProperties.networkInterfaces[0]
    def bootDiskInitializeParams = instanceTemplateProperties.disks[0].initializeParams

    return new BaseGoogleInstanceDescription(
      image: getLocalName(bootDiskInitializeParams.sourceImage),
      instanceType: instanceTemplateProperties.machineType,
      diskType: bootDiskInitializeParams.diskType,
      diskSizeGb: bootDiskInitializeParams.diskSizeGb,
      instanceMetadata: instanceTemplateProperties.metadata?.items?.collectEntries {
        [it.key, it.value]
      },
      tags: instanceTemplateProperties.tags?.items,
      network: getLocalName(networkInterface.network)
    )
  }

  static String buildDiskTypeUrl(String projectName, String zone, String diskType) {
    return "https://www.googleapis.com/compute/v1/projects/$projectName/zones/$zone/diskTypes/$diskType"
  }

  static AttachedDisk buildAttachedDisk(String projectName,
                                        String zone,
                                        Image sourceImage,
                                        Long diskSizeGb,
                                        String diskType,
                                        boolean useDiskTypeUrl,
                                        String instanceType,
                                        GceConfig.DeployDefaults deployDefaults) {
    if (!diskSizeGb || !diskType) {
      def defaultPersistentDisk = deployDefaults.determinePersistentDisk(instanceType)

      if (!diskSizeGb) {
        diskSizeGb = defaultPersistentDisk.size
      }

      if (!diskType) {
        diskType = defaultPersistentDisk.type
      }
    }

    if (useDiskTypeUrl) {
      diskType = buildDiskTypeUrl(projectName, zone, diskType)
    }

    def attachedDiskInitializeParams = new AttachedDiskInitializeParams(sourceImage: sourceImage.selfLink,
                                                                        diskSizeGb: diskSizeGb,
                                                                        diskType: diskType)

    return new AttachedDisk(boot: true,
                            autoDelete: true,
                            type: DISK_TYPE_PERSISTENT,
                            initializeParams: attachedDiskInitializeParams)
  }

  static NetworkInterface buildNetworkInterface(Network network, String accessConfigName, String accessConfigType) {
    def accessConfig = new AccessConfig(name: accessConfigName, type: accessConfigType)

    return new NetworkInterface(network: network.selfLink, accessConfigs: [accessConfig])
  }

  static Metadata buildMetadataFromMap(Map<String, String> instanceMetadata) {
    def itemsList = []

    if (instanceMetadata != null) {
      itemsList = instanceMetadata.collect { key, value ->
        new Metadata.Items(key: key, value: value)
      }
    }

    return new Metadata(items: itemsList)
  }

  static Map<String, String> buildMapFromMetadata(Metadata metadata) {
    def map = metadata?.items?.collectEntries { Metadata.Items metadataItems ->
      [(metadataItems.key): metadataItems.value]
    }

    return map ?: [:]
  }

  static Tags buildTagsFromList(List<String> tagsList) {
    return new Tags(items: tagsList)
  }

  // TODO(duftler/odedmeri): We should determine if there is a better approach than this naming convention.
  static List<String> deriveNetworkLoadBalancerNamesFromTargetPoolUrls(List<String> targetPoolUrls) {
    if (targetPoolUrls) {
      return targetPoolUrls.collect { targetPoolUrl ->
        def targetPoolLocalName = getLocalName(targetPoolUrl)

        targetPoolLocalName.split("-$TARGET_POOL_NAME_PREFIX-")[0]
      }
    } else {
      return []
    }
  }

  static def getNextSequence(String clusterName,
                             String project,
                             String region,
                             GoogleCredentials credentials) {
    def maxSeqNumber = -1
    def managedInstanceGroups = queryManagedInstanceGroups(project, region, credentials)

    for (def managedInstanceGroup : managedInstanceGroups) {
      def names = Names.parseName(managedInstanceGroup.getName())

      if (names.cluster == clusterName) {
        maxSeqNumber = Math.max(maxSeqNumber, names.sequence)
      }
    }

    String.format("%03d", ++maxSeqNumber)
  }

  static def combineAppStackDetail(String appName, String stack, String detail) {
    NameValidation.notEmpty(appName, "appName");

    // Use empty strings, not null references that output "null"
    stack = stack != null ? stack : "";

    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }

    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }

    return appName;
  }

  private static void updateStatusAndThrowNotFoundException(String errorMsg, Task task, String phase) {
    task.updateStatus phase, errorMsg
    throw new GoogleResourceNotFoundException(errorMsg)
  }

  public static String getLocalName(String fullUrl) {
    if (!fullUrl) {
      return fullUrl
    }

    def urlParts = fullUrl.split("/")

    return urlParts[urlParts.length - 1]
  }

  static def buildHttpHealthCheck(String name, UpsertGoogleLoadBalancerDescription.HealthCheck healthCheckDescription) {
    return new HttpHealthCheck(
        name: name,
        checkIntervalSec: healthCheckDescription.checkIntervalSec,
        timeoutSec: healthCheckDescription.timeoutSec,
        healthyThreshold: healthCheckDescription.healthyThreshold,
        unhealthyThreshold: healthCheckDescription.unhealthyThreshold,
        port: healthCheckDescription.port,
        requestPath: healthCheckDescription.requestPath)
  }

  // I know this is painfully similar to the method above. I will soon make a cleanup change to remove this ugliness.
  // TODO(bklingher): Clean this up.
  static def makeHttpHealthCheck(String name, CreateGoogleHttpLoadBalancerDescription.HealthCheck healthCheckDescription) {
    if (healthCheckDescription) {
      return new HttpHealthCheck(
          name: name,
          checkIntervalSec: healthCheckDescription.checkIntervalSec,
          timeoutSec: healthCheckDescription.timeoutSec,
          healthyThreshold: healthCheckDescription.healthyThreshold,
          unhealthyThreshold: healthCheckDescription.unhealthyThreshold,
          port: healthCheckDescription.port,
          requestPath: healthCheckDescription.requestPath)
    } else {
      return new HttpHealthCheck(
          name: name
      )
    }
  }

  static Firewall buildFirewallRule(String projectName,
                                    UpsertGoogleSecurityGroupDescription securityGroupDescription,
                                    Compute compute,
                                    Task task,
                                    String phase) {
    def network = queryNetwork(projectName, securityGroupDescription.network, compute, task, phase)
    def firewall = new Firewall(
        name: securityGroupDescription.securityGroupName,
        network: network.selfLink
    )
    def allowed = securityGroupDescription.allowed.collect {
      new Firewall.Allowed(IPProtocol: it.ipProtocol, ports: it.portRanges)
    }

    if (allowed) {
      firewall.allowed = allowed
    }

    if (securityGroupDescription.description) {
      firewall.description = securityGroupDescription.description
    }

    if (securityGroupDescription.sourceRanges) {
      firewall.sourceRanges = securityGroupDescription.sourceRanges
    }

    if (securityGroupDescription.sourceTags) {
      firewall.sourceTags = securityGroupDescription.sourceTags
    }

    if (securityGroupDescription.targetTags) {
      firewall.targetTags = securityGroupDescription.targetTags
    }

    return firewall
  }
}
