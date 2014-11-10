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

import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.ServerGroup
import groovy.transform.CompileStatic

//@CompileStatic
class AmazonServerGroup extends HashMap implements ServerGroup, Serializable {

  AmazonServerGroup() {
    this(null, null, null)
  }

  AmazonServerGroup(String name, String type, String region) {
    setProperty "name", name
    setProperty "type", type
    setProperty "region", region
    setProperty "zones", new HashSet<>()
    setProperty "instances", new HashSet<>()
    setProperty "health", new HashSet<>()
  }

  @Override
  String getName() {
    getProperty "name"
  }

  @Override
  String getType() {
    getProperty "type"
  }

  @Override
  String getRegion() {
    getProperty "region"
  }

  @Override
  Boolean isDisabled() {
    def asg = getAsg()
    if (asg) {
      List<Map> suspendedProcesses = asg.suspendedProcesses
      return suspendedProcesses.processName.containsAll(['AddToLoadBalancer', 'Launch', 'Terminate'])
    }
    return false
  }

  @Override
  Long getCreatedTime() {
    def asg = getAsg()
    if (asg) {
      return asg.createdTime
    }
    return null
  }

  @Override
  Set<String> getLoadBalancers() {
    def loadBalancerNames = []
    def asg = getAsg()
    if (asg && asg.containsKey("loadBalancerNames")) {
      loadBalancerNames = asg.loadBalancerNames
    }
    return loadBalancerNames
  }

  @Override
  Set<String> getSecurityGroups() {
    def securityGroups = []
    def launchConfig = getLaunchConfig()
    if (launchConfig && launchConfig.containsKey("securityGroups")) {
      securityGroups = launchConfig.securityGroups
    }
    securityGroups
  }

  @Override
  Set<String> getZones() {
    (Set<String>) getProperty("zones")
  }

  @Override
  Set<Instance> getInstances() {
    (Set<Instance>) getProperty("instances")
  }

  @Override
  Map<String, Object> getLaunchConfig() {
    (Map<String, Object>) getProperty("launchConfig")
  }

  Map getBuildInfo() {
    (Map) getProperty("buildInfo")
  }

  String getVpcId() {
    (String) getProperty("vpcId")
  }

  private Map<String, Object> getAsg() {
    (Map<String, Object>) getProperty("asg")
  }

}
