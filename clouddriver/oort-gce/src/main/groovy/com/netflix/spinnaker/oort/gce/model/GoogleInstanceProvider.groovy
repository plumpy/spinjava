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

package com.netflix.spinnaker.oort.gce.model

import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.oort.model.InstanceProvider
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
@CompileStatic
class GoogleInstanceProvider implements InstanceProvider<GoogleInstance> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  GoogleResourceRetriever googleResourceRetriever

  @PostConstruct
  void init() {
    googleResourceRetriever = new GoogleResourceRetriever()
    googleResourceRetriever.init(accountCredentialsProvider)
  }

  @Override
  GoogleInstance getInstance(String account, String region, String id) {
    // TODO(duftler): Create a unit test.
    String serverGroupName = getInstanceGroupBaseName(id)
    Names nameParts = Names.parseName(serverGroupName)
    GoogleApplication googleApplication = (googleResourceRetriever.getApplicationsMap())[nameParts.app]

    if (googleApplication) {
      Map<String, Map<String, GoogleCluster>> accountNameToClustersMap = googleApplication.clusters
      Map<String, GoogleCluster> clusterMap = accountNameToClustersMap[account]

      if (clusterMap) {
        GoogleCluster cluster = clusterMap[nameParts.cluster]

        if (cluster) {
          GoogleServerGroup serverGroup = cluster.serverGroups.find { it.name == nameParts.group && it.region == region }

          if (serverGroup) {
            return (GoogleInstance) serverGroup.instances.find { it.name == id }
          }
        }
      }
    }

    return null
  }

  // Strip off the final segment of the instance id (the unique portion that is added onto the instance group name).
  private static String getInstanceGroupBaseName(String instanceId) {
    int lastIndex = instanceId.lastIndexOf('-')

    return lastIndex != -1 ? instanceId.substring(0, lastIndex) : instanceId
  }

}
