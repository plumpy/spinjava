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

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.model.Application
import com.netflix.spinnaker.oort.model.Cluster
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.model.LoadBalancer
import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.regex.Pattern

@Component
class CatsClusterProvider implements ClusterProvider<AmazonCluster> {

  private static final Pattern ACCOUNT_NAME = Pattern.compile(/([^\/]+)\/(.*)/)
  private static final Pattern ACCOUNT_REGION_NAME = Pattern.compile(/([^\/]+)\/([^\/]+)\/(.*)/)

  private final Cache cacheView

  @Autowired
  CatsClusterProvider(Cache cacheView) {
    this.cacheView = cacheView
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(Cluster.DATA_TYPE)
    Collection<AmazonCluster> clusters = clusterData.findResults this.&translate
    mapResponse(clusters)
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters(String applicationName) {
    CacheData application = cacheView.get(Application.DATA_TYPE, applicationName)
    if (application == null) {
      return [:]
    }

    Collection<AmazonCluster> clusters = resolveRelationshipData(application, Cluster.DATA_TYPE).findResults this.&translate
    mapResponse(clusters)
  }

  Map<String, Set<AmazonCluster>> mapResponse(Collection<AmazonCluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  AmazonCluster translate(CacheData clusterData) {
    def matcher = ACCOUNT_NAME.matcher(clusterData.id)
    if (!matcher.matches()) {
      return null
    }

    def cluster = new AmazonCluster()
    cluster.accountName = matcher.group(1)
    cluster.name = matcher.group(2)

    Collection<AmazonLoadBalancer> loadBalancers = resolveRelationshipData(clusterData, LoadBalancer.DATA_TYPE).findResults this.&translateLoadBalancer
    cluster.loadBalancers.addAll(loadBalancers)

    Collection<AmazonServerGroup> serverGroups = resolveRelationshipData(clusterData, ServerGroup.DATA_TYPE).findResults this.&translateServerGroup
    cluster.serverGroups.addAll(serverGroups)

    cluster
  }

  AmazonLoadBalancer translateLoadBalancer(CacheData loadBalancerData) {
    def matcher = ACCOUNT_REGION_NAME.matcher(loadBalancerData.id)
    if (!matcher.matches()) {
      return null
    }

    new AmazonLoadBalancer(matcher.group(3), matcher.group(2))
  }

  AmazonServerGroup translateServerGroup(CacheData serverGroupData) {
    def matcher = ACCOUNT_REGION_NAME.matcher(serverGroupData.id)
    if (!matcher.matches()) {
      return null
    }

    def sg = new AmazonServerGroup(matcher.group(3), 'aws', matcher.group(2))
    sg.putAll(serverGroupData.attributes)
    sg
  }

  Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    source.relationships[relationship]?.findResults { cacheView.get(relationship, it) } ?: [] as Collection
  }

  @Override
  Set<AmazonCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(Application.DATA_TYPE, applicationName)
    if (application == null) {
      return [] as Set
    }
    resolveRelationshipData(application, Cluster.DATA_TYPE).findResults(this.&translate) as Set<AmazonCluster>
  }

  @Override
  AmazonCluster getCluster(String application, String account, String name) {
    CacheData cluster = cacheView.get(Cluster.DATA_TYPE, "$account/$name")
    if (cluster == null) {
      null
    } else {
      translate(cluster)
    }
  }
}
