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

package com.netflix.spinnaker.oort.applications

import com.netflix.spinnaker.oort.clusters.ClusterProvider
import com.netflix.spinnaker.oort.clusters.Clusters
import com.netflix.spinnaker.oort.spring.ApplicationContextHolder
import org.springframework.beans.factory.annotation.Autowired

class Application {

  @Autowired
  private List<ClusterProvider> clusterProviders

  Application() {
    ApplicationContextHolder.applicationContext.autowireCapableBeanFactory.autowireBean(this)
  }

  String name
  String type
  Map<String, String> attributes

  private Map<String, Clusters> accountClusters = [:]

  Clusters getClusters(String account) {
    if (!accountClusters.containsKey(account)) {
      Clusters clusters = new Clusters()
      for (provider in clusterProviders) {
        def providerClusters = provider.getSummary(this.name, account)
        if (providerClusters) {
          clusters.addAll providerClusters
        }
      }
      accountClusters[account] = clusters
    }
    accountClusters[account]
  }

  static Application merge(Application a, Application b) {
    assert a.name == b.name
    def deployable = new Application(name: a.name, attributes: [:])
    if (a.attributes) {
      deployable.attributes << a.attributes
    }
    if (b.attributes) {
      deployable.attributes << b.attributes
    }
    deployable
  }
}
