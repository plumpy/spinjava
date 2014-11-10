/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.provider.aws.view

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.model.aws.AmazonInstance
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import com.netflix.spinnaker.oort.model.aws.AwsInstanceHealth
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.LAUNCH_CONFIGS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.SERVER_GROUPS

@Component
class CatsClusterProvider implements ClusterProvider<AmazonCluster> {

  private final Cache cacheView

  @Autowired
  CatsClusterProvider(Cache cacheView) {
    this.cacheView = cacheView
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    Collection<AmazonCluster> clusters = translateClusters(clusterData, false)
    mapResponse(clusters)
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters(String applicationName, boolean includeDetails) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) {
      return [:]
    }
    Collection<AmazonCluster> clusters = translateClusters(resolveRelationshipData(application, CLUSTERS.ns), includeDetails)
    mapResponse(clusters)
  }

  @Override
  AmazonServerGroup getServerGroup(String account, String region, String name) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region)
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey)
    Collection<CacheData> launchConfigs = resolveRelationshipData(serverGroupData, LAUNCH_CONFIGS.ns)
    def asg = serverGroupData.attributes["asg"]
    def serverGroup = new AmazonServerGroup()
    serverGroup.accountName = account
    serverGroup.name = name
    serverGroup.region = region
    serverGroup.zones = serverGroupData.attributes["zones"]
    serverGroup.launchConfig = launchConfigs ? launchConfigs[0].attributes : null
    serverGroup.asg = asg
    serverGroup.instances = translateInstances(resolveRelationshipData(serverGroupData, INSTANCES.ns)).values()

    serverGroup
  }

  private Map<String, Set<AmazonCluster>> mapResponse(Collection<AmazonCluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  private Collection<AmazonCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {

    Map<String, AmazonLoadBalancer> loadBalancers
    Map<String, AmazonServerGroup> serverGroups

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(clusterData, LOAD_BALANCERS.ns)
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, SERVER_GROUPS.ns)

      loadBalancers = translateLoadBalancers(allLoadBalancers)
      serverGroups = translateServerGroups(allServerGroups)
    }

    Collection<AmazonCluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)

      def cluster = new AmazonCluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.cluster
      if (includeDetails) {
        cluster.loadBalancers = clusterDataEntry.relationships[LOAD_BALANCERS.ns]?.collect { loadBalancers.get(it) }
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.collect { serverGroups.get(it) }
      } else {
        cluster.loadBalancers = clusterDataEntry.relationships[LOAD_BALANCERS.ns]?.collect { loadBalancerKey ->
          Map parts = Keys.parse(loadBalancerKey)
          new AmazonLoadBalancer(parts.loadBalancer, parts.region)
        }
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.collect { serverGroupKey ->
          Map parts = Keys.parse(serverGroupKey)
          new AmazonServerGroup(parts.serverGroup, "aws", parts.region)
        }
      }
      cluster
    }

    clusters
  }

  private Map<String, AmazonLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      Map<String, String> lbKey = Keys.parse(loadBalancerEntry.id)
      [(loadBalancerEntry.id) : new AmazonLoadBalancer(lbKey.loadBalancer, lbKey.region)]
    }
  }

  private Map<String, AmazonServerGroup> translateServerGroups(Collection<CacheData> serverGroupData) {
    Collection<CacheData> allInstances = resolveRelationshipDataForCollection(serverGroupData, INSTANCES.ns)

    Map<String, AmazonInstance> instances = translateInstances(allInstances)

    Map<String, AmazonServerGroup> serverGroups = serverGroupData.collectEntries { serverGroupEntry ->
      Map<String, String> serverGroupKey = Keys.parse(serverGroupEntry.id)

      def serverGroup = new AmazonServerGroup(serverGroupKey.serverGroup, 'aws', serverGroupKey.region)
      serverGroup.putAll(serverGroupEntry.attributes)
      serverGroup.instances = serverGroupEntry.relationships[INSTANCES.ns]?.collect { instances.get(it) } - null
      [(serverGroupEntry.id) : serverGroup]
    }

    Map<String, String> launchConfigurations = serverGroupData.findAll { it.relationships[LAUNCH_CONFIGS.ns] }.collectEntries {
      [(it.relationships[LAUNCH_CONFIGS.ns].first()) : it.id]
    }
    Collection<CacheData> launchConfigs = cacheView.getAll(LAUNCH_CONFIGS.ns, launchConfigurations.keySet())
    Map<String, Collection<String>> allImages = [:]
    launchConfigs.each { launchConfig ->
      def serverGroupId = launchConfigurations[launchConfig.id]
      def imageId = launchConfig.relationships[IMAGES.ns]?.first()
      serverGroups[serverGroupId].launchConfig = launchConfig.attributes
      if (imageId) {
        if (!allImages.containsKey(imageId)) {
          allImages.put(imageId, [])
        }
        allImages[imageId] << serverGroupId
      }
    }
    Collection<CacheData> images = cacheView.getAll(IMAGES.ns, allImages.keySet())
    images.each { image ->
      def serverGroupIds = allImages[image.id]
      Map buildInfo = null

      String appVersionTag = image.attributes.tags?.find { it.key == "appversion" }?.value
      if (appVersionTag) {
        def appVersion = AppVersion.parseName(appVersionTag)
        if (appVersion) {
          buildInfo = [package_name: appVersion.packageName, version: appVersion.version, commit: appVersion.commit] as Map<Object, Object>
          if (appVersion.buildJobName) {
            buildInfo.jenkins = [name: appVersion.buildJobName, number: appVersion.buildNumber]
          }
          def buildHost = image.attributes.tags.find { it.key == "build_host" }?.value ?: "http://builds.netflix.com/"
          if (buildHost) {
            ((Map) buildInfo.jenkins).host = buildHost
          }
        }
      }

      serverGroupIds.each { serverGroupId ->
        def serverGroup = serverGroups[serverGroupId]
        serverGroup.image = image.attributes
        serverGroup.buildInfo = buildInfo
      }
    }

    serverGroups
  }

  private Map<String, AmazonInstance> translateInstances(Collection<CacheData> instanceData) {
    Map<String, AmazonInstance> instances = instanceData.collectEntries { instanceEntry ->
      Map<String, String> instanceKey = Keys.parse(instanceEntry.id)
      HealthState amazonState = instanceEntry.attributes.state?.code == 16 ? HealthState.Unknown : HealthState.Down
      Collection<Map<String, Object>> healths = []
      Map awsInstanceHealth = new AwsInstanceHealth(type: 'Amazon', instanceId: instanceKey.instanceId, state: amazonState.toString()).properties
      awsInstanceHealth.remove('class')
      healths << awsInstanceHealth

      AmazonInstance instance = new AmazonInstance(instanceEntry.attributes.instanceId.toString())
      instance.putAll(instanceEntry.attributes)
      instance.health = healths
      instance.zone = instanceEntry.attributes.placement?.availabilityZone?: null
      [(instanceEntry.id): instance]
    }
    addHealthToInstances(instanceData, instances)

    instances
  }

  private void addHealthToInstances(Collection<CacheData> instanceData, Map<String, AmazonInstance> instances) {
    Map<String, String> healthKeysToInstance = [:]
    instanceData.each { instanceEntry ->
      Map<String, String> instanceKey = Keys.parse(instanceEntry.id)
      AwsProvider.HEALTH_PROVIDERS.each {
        def key = Keys.getInstanceHealthKey(instanceKey.instanceId, instanceKey.account, instanceKey.region, it)
        healthKeysToInstance.put(key, instanceEntry.id)
      }
    }

    Collection<CacheData> healths = cacheView.getAll(HEALTH.ns, healthKeysToInstance.keySet())
    healths.each { healthEntry ->
      def instanceId = healthKeysToInstance.get(healthEntry.id)
      instances[instanceId].health << healthEntry.attributes
    }

    instances.values().each { instance ->
      instance.isHealthy = instance.health.any { it.state == 'Up' } && !instance.health.any { it.state == 'Down' }
    }
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship) {
    Collection<String> relationships = sources.collect { it.relationships[relationship]?: [] }.flatten()
    relationships ? cacheView.getAll(relationship, relationships) : []
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    resolveRelationshipData(source, relationship) { true }
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }

  @Override
  Set<AmazonCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) {
      return [] as Set
    }
    Collection<String> clusterKeys = application.relationships[CLUSTERS.ns].findAll { Keys.parse(it).account == account }
    Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys)
    translateClusters(clusters, true) as Set<AmazonCluster>
  }

  @Override
  AmazonCluster getCluster(String application, String account, String name) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account))
    if (cluster == null) {
      null
    } else {
      translateClusters([cluster], true)[0]
    }
  }
}
