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

package com.netflix.asgard.oort.controllers

import com.netflix.asgard.oort.clusters.ClusterProvider
import com.netflix.asgard.oort.deployables.Deployable
import com.netflix.asgard.oort.deployables.DeployableProvider
import com.netflix.asgard.oort.remoting.AggregateRemoteResource
import javax.servlet.http.HttpServletResponse

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/deployables/{deployable}/clusters")
class ClusterController {

  @Autowired
  AggregateRemoteResource edda

  @Autowired
  List<DeployableProvider> deployableProviders

  @Autowired
  List<ClusterProvider> clusterProviders

  @RequestMapping(method = RequestMethod.GET)
  def list(@PathVariable("deployable") String deployable) {
    Map<String, Deployable> deployables = [:]
    deployableProviders.each {
      def deployableObject = it.get(deployable)
      if (!deployableObject) return

      if (deployables.containsKey(deployableObject.name)) {
        def existing = deployables[deployableObject.name]
        deployables[deployableObject.name] = Deployable.merge(existing, deployableObject)
      } else {
        deployables[deployableObject.name] = deployableObject
      }
    }
    deployables.values()?.getAt(0)?.clusters?.list()
  }

  @RequestMapping(value = "/{cluster}", method = RequestMethod.GET)
  def get(@PathVariable("deployable") String deployable, @PathVariable("cluster") String clusterName,
          @RequestParam(value = "zone", required = false) String zoneName) {
    clusterProviders.collect {
      zoneName ? [it.getByNameAndZone(deployable, clusterName, zoneName)] : it.getByName(deployable, clusterName)
    }?.flatten()
  }

  @RequestMapping(value = "/{cluster}/serverGroups/{serverGroup}/{zone}", method = RequestMethod.GET)
  def getAsg(@PathVariable("deployable") String deployable, @PathVariable("cluster") String clusterName,
             @PathVariable("serverGroup") String serverGroupName, @PathVariable("zone") String zoneName, HttpServletResponse response) {

    def serverGroup
    for (provider in clusterProviders) {
      serverGroup = provider.getByNameAndZone(deployable, clusterName, zoneName).serverGroups.find { it.name == serverGroupName }
      if (serverGroup) {
        def copied = new HashMap(serverGroup)
        copied.instances = copied.instances.collect { getInstance zoneName, it.instanceId }
        return copied
      }
    }
    response.sendError 404
  }

  def getInstance(String region, String instanceId) {
    try {
      edda.getRemoteResource(region) get "/REST/v2/view/instances/$instanceId"
    } catch (IGNORE) { [instanceId: instanceId, state: [name: "offline"]] }
  }
}
