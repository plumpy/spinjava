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

package com.netflix.spinnaker.oort.controllers

import com.netflix.spinnaker.oort.aws.model.edda.InstanceLoadBalancers
import com.netflix.spinnaker.oort.model.Cluster
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/applications/{application}/serverGroups")
class ServerGroupController {

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired
  MessageSource messageSource

  @RequestMapping(value="/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  ServerGroup getServerGroup(@PathVariable String account, @PathVariable String region, @PathVariable String name) {
    def matches = (Set<ServerGroup>) clusterProviders.findResults {
      it.getServerGroup(account, region, name)
    }
    if (!matches) {
      throw new ServerGroupNotFoundException([name: name, account: account, region: region])
    }
    matches.first()
  }

  @RequestMapping(method = RequestMethod.GET)
  List<ServerGroupViewModel> list(@PathVariable String application) {
    List<ServerGroupViewModel> serverGroupViews = []

    def clusters = (Set<Cluster>) clusterProviders.findResults {
      it.getClusterDetails(application)?.values()
    }.flatten()
    clusters.each { Cluster cluster ->
      cluster.serverGroups.each { ServerGroup serverGroup ->
        serverGroupViews << new ServerGroupViewModel(serverGroup, cluster)
      }
    }

    serverGroupViews
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleServerGroupNotFoundException(ServerGroupNotFoundException ex) {
    def message = messageSource.getMessage("serverGroup.not.found", [ex.name, ex.account, ex.region] as String[], "serverGroup.not.found", LocaleContextHolder.locale)
    [error: "serverGroup.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  static class ServerGroupNotFoundException extends RuntimeException {
    String name
    String account
    String region
  }

  static class ServerGroupViewModel {
    String name
    String account
    String region
    String cluster
    String vpcId
    String type
    String instanceType
    Boolean isDisabled
    Map buildInfo
    Long createdTime
    List<InstanceViewModel> instances
    Set<String> loadBalancers
    Set<String> securityGroups
    ServerGroup.InstanceCounts instanceCounts

    ServerGroupViewModel(ServerGroup serverGroup, Cluster cluster) {
      this.cluster = cluster.name
      type = serverGroup.type
      name = serverGroup.name
      account = cluster.accountName
      region = serverGroup.region
      createdTime = serverGroup.getCreatedTime()
      isDisabled = serverGroup.isDisabled()
      instances = serverGroup.getInstances().findResults { it ? new InstanceViewModel(it) : null }
      instanceCounts = serverGroup.getInstanceCounts()
      securityGroups = serverGroup.getSecurityGroups()
      loadBalancers = serverGroup.getLoadBalancers()
      if (serverGroup.launchConfig) {
        instanceType = serverGroup.launchConfig.instanceType
      }
      if (serverGroup.hasProperty("buildInfo")) {
        buildInfo = serverGroup.buildInfo
      }
      if (serverGroup.hasProperty("vpcId")) {
        vpcId = serverGroup.vpcId
      }
    }
  }

  static class InstanceViewModel {
    String id
    List<Map<String, Object>> health
    Boolean isHealthy
    Long launchTime
    String availabilityZone

    InstanceViewModel(Instance instance) {
      id = instance.name
      isHealthy = instance.isHealthy()
      launchTime = instance.launchTime
      availabilityZone = instance.zone
      health = instance.health.collect { health ->
        Map healthMetric = [type: health.type]
        if (health.containsKey("state")) {
          healthMetric.state = health.state.toString()
        }
        if (health.containsKey("status")) {
          healthMetric.status = health.status
        }
        if (health.type == InstanceLoadBalancers.HEALTH_TYPE && health.containsKey("loadBalancers")) {
          healthMetric.loadBalancers = health.loadBalancers.collect {
            [name : it.loadBalancerName, state : it.state, description: it.description ]
          }
        }
        healthMetric
      }
    }
  }

}


