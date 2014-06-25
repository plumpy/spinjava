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

package com.netflix.spinnaker.oort.model.aws

import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.model.Health
import com.netflix.spinnaker.oort.model.HealthProvider
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.functions.Func1
import rx.functions.Func2
import rx.schedulers.Schedulers

@Component
@CompileStatic
class AmazonClusterProvider implements ClusterProvider<AmazonCluster> {
  @Autowired
  CacheService cacheService

  @Autowired
  List<HealthProvider> healthProviders

  @Override
  Map<String, Set<AmazonCluster>> getClusters() {
    def keys = cacheService.keys()
    def clusters = keys.findAll { it.startsWith("clusters:") }.collect { cacheService.retrieve(it, AmazonCluster) }
    getClustersWithServerGroups keys, clusters
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters(String application) {
    def keys = cacheService.keys()
    def clusters = keys.findAll { it.startsWith("clusters:${application}:") }.collect { cacheService.retrieve(it, AmazonCluster) }
    getClustersWithServerGroups keys, clusters
  }

  @Override
  Set<AmazonCluster> getClusters(String application, String accountName) {
    def keys = cacheService.keys()
    def clusters = keys.findAll { it.startsWith("clusters:${application}:${accountName}:") }.collect { cacheService.retrieve(it, AmazonCluster) }
    (Set<AmazonCluster>) getClustersWithServerGroups(keys, clusters)?.get(accountName)
  }

  @Override
  AmazonCluster getCluster(String application, String account, String name) {
    def keys = cacheService.keys()
    def cluster = cacheService.retrieve(Keys.getClusterKey(name, application, account), AmazonCluster)
    if (cluster) {
      def withServerGroups = getClustersWithServerGroups(keys, [cluster])
      cluster = withServerGroups[account]?.getAt(0)
    }
    cluster
  }

  private Map<String, Set<AmazonCluster>> getClustersWithServerGroups(Set<String> keys, List<AmazonCluster> clusters) {
    rx.Observable.from(clusters).flatMap {
      rx.Observable.from(it).observeOn(Schedulers.io()).map { cluster ->
        clusterFiller(keys, cluster as AmazonCluster)
        cluster
      }
    }.reduce([:], { Map results, AmazonCluster cluster ->
      if (!results.containsKey(cluster.accountName)) {
        results[cluster.accountName] = new HashSet<>()
      }
      ((Set)results[cluster.accountName as String]) << cluster
      results
    } as Func2<Map, ? super AmazonCluster, Map>).toBlocking().first()
  }

  void clusterFiller(Set<String> keys, AmazonCluster cluster) {
    def serverGroups = keys.findAll { it.startsWith("serverGroups:${cluster.name}:${cluster.accountName}:") }
    if (!serverGroups) return
    for (loadBalancer in cluster.loadBalancers) {
      def elb = keys.find { it == "loadBalancers:${loadBalancer.region}:${loadBalancer.name}:" }
      if (elb) {
        loadBalancer.elb = cacheService.retrieve(elb, LoadBalancerDescription)
      }
    }

    cluster.serverGroups = (Set)rx.Observable.from(serverGroups)
      .map({ String name -> cacheService.retrieve(name, AmazonServerGroup) })
      .filter({ it != null })
      .map(attributePopulator.curry(keys) as Func1)
      .map(instancePopulator.curry(keys, cluster) as Func1)
      .reduce(new HashSet<AmazonServerGroup>(), { Set<AmazonServerGroup> objs, AmazonServerGroup obj -> objs << obj })
      .doOnError({ Throwable t -> t.printStackTrace() })
      .toBlocking()
      .firstOrDefault(new HashSet<AmazonServerGroup>()) as Set
  }

  final Closure attributePopulator = { Set<String> keys, AmazonServerGroup serverGroup ->
    def serverAttributes = new ServerGroupAttributeBuilder(keys, serverGroup)
    serverGroup.launchConfig = serverAttributes.launchConfig
    serverGroup.image = serverAttributes.image
    serverGroup.buildInfo = serverAttributes.buildInfo
    serverGroup
  }

  final Closure instancePopulator = { Set<String> keys, AmazonCluster cluster, AmazonServerGroup serverGroup ->
    def instanceIds = keys.findAll { it.startsWith("serverGroupsInstance:${cluster.name}:${cluster.accountName}:${serverGroup.region}:${serverGroup.name}:") }.collect { it.split(':')[-1] }
    for (instanceId in instanceIds) {
      def ec2Instance = cacheService.retrieve(Keys.getInstanceKey(instanceId, serverGroup.region), Instance)
      if (ec2Instance) {
        def modelInstance = new AmazonInstance(instanceId)
        modelInstance.instance = ec2Instance
        modelInstance.health = healthProviders.collect { it.getHealth(cluster.accountName, serverGroup, instanceId) }
        modelInstance.isHealthy = !((List<Health>)modelInstance.health)?.find { !it?.isHealthy() ?: false }
        serverGroup.instances << modelInstance
      }
    }
    serverGroup
  }

  private class ServerGroupAttributeBuilder {
    Set<String> keys
    AmazonServerGroup serverGroup

    ServerGroupAttributeBuilder(Set<String> keys, AmazonServerGroup serverGroup) {
      this.keys = keys
      this.serverGroup = serverGroup
    }

    private LaunchConfiguration launchConfiguration
    private Image image
    private Map buildInfo

    LaunchConfiguration getLaunchConfig() {
      if (!this.launchConfiguration) {
        def launchConfigKey = Keys.getLaunchConfigKey(serverGroup.launchConfigName as String, serverGroup.region)
        this.launchConfiguration = keys.contains(launchConfigKey) ? cacheService.retrieve(launchConfigKey, LaunchConfiguration) : null
      }
      this.launchConfiguration
    }

    Image getImage() {
      if (!this.image) {
        def launchConfig = getLaunchConfig()
        if (!launchConfig) return null
        def imageKey = Keys.getImageKey(launchConfig.imageId, serverGroup.region)
        this.image = keys.contains(imageKey) ? cacheService.retrieve(imageKey, Image) : null
      }
      this.image
    }

    Map getBuildInfo() {
      if (!this.buildInfo) {
        def appVersionTag = image?.tags?.find { it.key == "appversion" }?.value
        if (appVersionTag) {
          def appVersion = AppVersion.parseName(appVersionTag)
          if (appVersion) {
            buildInfo = [package_name: appVersion.packageName, version: appVersion.version, commit: appVersion.commit] as Map<Object, Object>
            if (appVersion.buildJobName) {
              buildInfo.jenkins = [:] << [name: appVersion.buildJobName, number: appVersion.buildNumber]
            }
            def buildHost = image.tags.find { it.key == "build_host" }?.value
            if (buildHost) {
              ((Map) buildInfo.jenkins).host = buildHost
            }
          }
        }
      }
      buildInfo
    }
  }
}
