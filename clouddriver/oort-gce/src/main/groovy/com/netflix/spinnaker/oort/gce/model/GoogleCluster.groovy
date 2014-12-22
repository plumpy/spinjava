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

import com.netflix.spinnaker.oort.model.Cluster
import com.netflix.spinnaker.oort.model.LoadBalancer
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name", "accountName"])
class GoogleCluster implements Cluster, Serializable {
  String name
  // TODO(duftler): Once the UI can request a type other than 'aws', fix this.
//  String type = "gce"
  String type = "aws"
  String accountName
  Set<GoogleServerGroup> serverGroups = Collections.synchronizedSet(new HashSet<GoogleServerGroup>())
  Set<LoadBalancer> loadBalancers = Collections.synchronizedSet(new HashSet<LoadBalancer>())

  // Used as a deep copy-constructor.
  public static GoogleCluster newInstance(GoogleCluster originalGoogleCluster) {
    GoogleCluster copyGoogleCluster = new GoogleCluster(name: originalGoogleCluster.name,
                                                        type: originalGoogleCluster.type,
                                                        accountName: originalGoogleCluster.accountName)

    originalGoogleCluster.serverGroups.each { originalServerGroup ->
      copyGoogleCluster.serverGroups << GoogleServerGroup.newInstance(originalServerGroup)
    }

    // TODO(duftler): Flesh this out when GoogleLoadBalancer exists (which will be shortly).
//    originalGoogleCluster.loadBalancers.each { originalLoadBalancer ->
//    }

    copyGoogleCluster
  }
}
