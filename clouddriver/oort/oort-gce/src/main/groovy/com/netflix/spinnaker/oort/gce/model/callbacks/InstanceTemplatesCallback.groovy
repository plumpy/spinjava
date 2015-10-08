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

package com.netflix.spinnaker.oort.gce.model.callbacks

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceTemplate
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.mort.gce.model.GoogleSecurityGroup
import com.netflix.spinnaker.oort.gce.model.GoogleCluster
import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import org.apache.log4j.Logger

class InstanceTemplatesCallback<InstanceTemplate> extends JsonBatchCallback<InstanceTemplate> {
  protected static final Logger log = Logger.getLogger(this)

  private static final String LOAD_BALANCER_NAMES = "load-balancer-names"

  private InstanceGroupManager instanceGroupManager
  private GoogleServerGroup googleServerGroup
  private GoogleCluster googleCluster
  private Set<GoogleSecurityGroup> googleSecurityGroups
  private Map<String, List<Map>> imageMap
  private String defaultBuildHost

  public InstanceTemplatesCallback(InstanceGroupManager instanceGroupManager,
                                   GoogleServerGroup googleServerGroup,
                                   GoogleCluster googleCluster,
                                   Set<GoogleSecurityGroup> googleSecurityGroups,
                                   Map<String, List<Map>> imageMap,
                                   String defaultBuildHost) {
    this.instanceGroupManager = instanceGroupManager
    this.googleServerGroup = googleServerGroup
    this.googleCluster = googleCluster
    this.googleSecurityGroups = googleSecurityGroups
    this.imageMap = imageMap
    this.defaultBuildHost = defaultBuildHost
  }

  @Override
  void onSuccess(InstanceTemplate instanceTemplate, HttpHeaders responseHeaders) throws IOException {
    googleServerGroup.launchConfig.launchConfigurationName = instanceTemplate?.name
    googleServerGroup.launchConfig.instanceType = instanceTemplate?.properties?.machineType

    def sourceImageUrl = instanceTemplate?.properties?.disks?.find { disk ->
      disk.boot
    }?.initializeParams?.sourceImage

    if (sourceImageUrl) {
      def sourceImageName = Utils.getLocalName(sourceImageUrl)

      googleServerGroup.launchConfig.imageId = sourceImageName

      def sourceImage = imageMap[googleCluster.accountName]?.find { image ->
        image.name == sourceImageName
      }

      extractBuildInfo(sourceImage?.description, googleServerGroup, defaultBuildHost)
    }

    def instanceMetadata = instanceTemplate?.properties?.metadata

    if (instanceMetadata) {
      def metadataMap = Utils.buildMapFromMetadata(instanceMetadata)

      if (metadataMap) {
        if (metadataMap[LOAD_BALANCER_NAMES]) {
          def loadBalancerNameList = metadataMap[LOAD_BALANCER_NAMES].split(",")

          if (loadBalancerNameList) {
            googleServerGroup.asg.loadBalancerNames = loadBalancerNameList

            // Collect all load balancer names at the cluster level as well.
            for (loadBalancerName in loadBalancerNameList) {
              if (!googleCluster.loadBalancers.find { it.name == loadBalancerName }) {
                googleCluster.loadBalancers << new GoogleLoadBalancer(name: loadBalancerName, account: googleCluster.accountName, region: googleServerGroup.region)
              }
            }
          }

          // The isDisabled property of a server group is set based on whether there are associated target pools.
          def targetPoolLoadBalancerNames =
            Utils.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(instanceGroupManager.getTargetPools())

          googleServerGroup.setDisabled(targetPoolLoadBalancerNames.empty)
        }
      }
    }

    // Find all firewall rules in this network with target tags matching the tags of this instance template.
    def networkName = Utils.getNetworkNameFromInstanceTemplate(instanceTemplate)
    def googleSecurityGroupMatches = [] as Set

    instanceTemplate?.properties?.tags?.items.each { instanceTemplateTag ->
      googleSecurityGroupMatches << googleSecurityGroups.findAll { googleSecurityGroup ->
        googleSecurityGroup.network == networkName && googleSecurityGroup.targetTags?.contains(instanceTemplateTag)
      }
    }

    // Find all firewall rules in this network with no target tags.
    googleSecurityGroupMatches << googleSecurityGroups.findAll { googleSecurityGroup ->
      googleSecurityGroup.network == networkName && !googleSecurityGroup.targetTags
    }

    googleServerGroup.securityGroups = googleSecurityGroupMatches.flatten().collect { googleSecurityGroup ->
      googleSecurityGroup.name
    }

    // Set all google-provided attributes for use by non-deck callers.
    googleServerGroup.launchConfig.instanceTemplate = instanceTemplate
  }

  static void extractBuildInfo(String imageDescription, GoogleServerGroup googleServerGroup, String defaultBuildHost) {
    if (imageDescription) {
      def descriptionTokens = imageDescription?.tokenize(",")
      def appVersionTag = findTagValue(descriptionTokens, "appversion")
      Map buildInfo = null

      if (appVersionTag) {
        def appVersion = AppVersion.parseName(appVersionTag)

        if (appVersion) {
          buildInfo = [package_name: appVersion.packageName, version: appVersion.version, commit: appVersion.commit] as Map<Object, Object>

          if (appVersion.buildJobName) {
            buildInfo.jenkins = [name: appVersion.buildJobName, number: appVersion.buildNumber]
          }

          def buildHostTag = findTagValue(descriptionTokens, "build_host") ?: defaultBuildHost

          if (buildHostTag && buildInfo.containsKey("jenkins")) {
            ((Map) buildInfo.jenkins).host = buildHostTag
          }
        }

        if (buildInfo) {
          googleServerGroup.setProperty("buildInfo", buildInfo)
        }
      }
    }
  }

  static String findTagValue(List<String> descriptionTokens, String tagKey) {
    def matchingKeyValuePair = descriptionTokens?.find { keyValuePair ->
      keyValuePair.trim().startsWith("$tagKey: ")
    }

    matchingKeyValuePair ? matchingKeyValuePair.trim().substring(tagKey.length() + 2) : null
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}
