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

package com.netflix.spinnaker.gate.services
import com.netflix.frigga.Names
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.OortService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class ClusterService {
  private static final String GROUP = "clusters"

  @Autowired
  OortService oortService

  @Autowired
  TagService tagService

  Map getClusters(String app) {
    HystrixFactory.newMapCommand(GROUP, "getClustersForApplication", true) {
      oortService.getClusters(app)
    } execute()
  }

  List<Map> getClustersForAccount(String app, String account) {
    HystrixFactory.newListCommand(GROUP, "getClustersForApplicationInAccount", true) {
      oortService.getClustersForAccount(app, account)
    } execute()
  }

  Map getCluster(String app, String account, String clusterName) {
    HystrixFactory.newMapCommand(GROUP, "getCluster", true) {
      oortService.getCluster(app, account, clusterName)?.getAt(0) as Map
    } execute()
  }

  List<Map> getClusterServerGroups(String app, String account, String clusterName) {
    getCluster(app, account, clusterName).serverGroups as List<Map>
  }

  List<String> getClusterTags(String clusterName) {
    def names = Names.parseName(clusterName)
    tagService.getTags(names.app).findAll() {
      it.item == "cluster" && (it.name as String).toLowerCase() == clusterName.toLowerCase()
    }.collect {
      it.tags
    }
  }

  List<Map> getScalingActivities(String app, String account, String clusterName, String serverGroupName, String provider, String region) {
    HystrixFactory.newListCommand(GROUP, "getScalingActivitiesForCluster", true) {
      oortService.getScalingActivities(app, account, clusterName, provider, serverGroupName, region)
    } execute()
  }
}
