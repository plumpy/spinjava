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

import com.netflix.spinnaker.oort.model.*
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/applications/{application}/clusters")
class ClusterController {

  @Autowired
  List<ApplicationProvider> applicationProviders

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired
  MessageSource messageSource

  @RequestMapping(method = RequestMethod.GET)
  Map<String, Set<String>> list(@PathVariable String application) {
    def apps = ((List<Application>) applicationProviders.collectMany {
      [it.getApplication(application)] ?: []
    }).findAll().sort { a, b -> a.name.toLowerCase() <=> b.name.toLowerCase() }
    def clusterNames = [:]
    def lastApp = null
    for (app in apps) {
      if (!lastApp) {
        clusterNames = app.clusterNames
      } else {
        clusterNames = Application.mergeClusters.curry(lastApp, app).call()
      }
      lastApp = app
    }
    clusterNames
  }

  @RequestMapping(value = "/{account}")
  Set<ClusterViewModel> getForAccount(@PathVariable String application, @PathVariable String account) {
    def clusters = (Set<ClusterViewModel>) clusterProviders.collect {
      def clusters = (Set<Cluster>) it.getClusters(application, account)
      def clusterViews = []
      for (cluster in clusters) {
        clusterViews << new ClusterViewModel(name: cluster.name, account: cluster.accountName, loadBalancers: cluster.loadBalancers.collect { it.name }, serverGroups: cluster.serverGroups.collect {
          it.name
        })
      }
      clusterViews
    }?.flatten() as Set
    if (!clusters) {
      throw new AccountClustersNotFoundException(application: application, account: account)
    }
    clusters
  }


  @RequestMapping(value = "/{account}/{name:.+}", method = RequestMethod.GET)
  Set<Cluster> getForAccountAndName(@PathVariable String application, @PathVariable String account, @PathVariable String name) {
    def clusters = clusterProviders.collect {
      it.getCluster(application, account, name)
    }
    clusters.removeAll([null])
    if (!clusters) {
      throw new ClusterNotFoundException(cluster: name)
    }
    clusters
  }

  @RequestMapping(value = "/{account}/{name}/{type}", method = RequestMethod.GET)
  Cluster getForAccountAndNameAndType(@PathVariable String application, @PathVariable String account, @PathVariable String name, @PathVariable String type) {
    Set<Cluster> allClusters = getForAccountAndName(application, account, name)
    def cluster = allClusters.find { it.type == type }
    if (!cluster) {
      throw new ClusterNotFoundException(cluster: name)
    }
    cluster
  }

  @RequestMapping(value = "/{account}/{clusterName}/{type}/serverGroups", method = RequestMethod.GET)
  Set<ServerGroup> getServerGroups(@PathVariable String application, @PathVariable String account, @PathVariable String clusterName, @PathVariable String type,
                                   @RequestParam(value = "region", required = false) String region) {
    Cluster cluster = getForAccountAndNameAndType(application, account, clusterName, type)
    def results = region ? cluster.serverGroups.findAll { it.region == region } : cluster.serverGroups
    results ?: []
  }

  @RequestMapping(value = "/{account}/{clusterName}/{type}/serverGroups/{serverGroupName:.+}", method = RequestMethod.GET)
  def getServerGroup(@PathVariable String application, @PathVariable String account, @PathVariable String clusterName, @PathVariable String type, @PathVariable String serverGroupName,
                     @RequestParam(value = "region", required = false) String region, @RequestParam(value = "health", required = false) Boolean health) {
    def serverGroups = getServerGroups(application, account, clusterName, type, region).findAll { region ? it.name == serverGroupName && it.region == region : it.name == serverGroupName }
    if (!serverGroups) {
      throw new ServerGroupNotFoundException(serverGroupName: serverGroupName)
    }
    region ? serverGroups?.getAt(0) : serverGroups
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleClusterNotFoundException(AccountClustersNotFoundException ex) {
    def message = messageSource.getMessage("account.clusters.not.found", [ex.application, ex.account] as String[], "account.clusters.not.found", LocaleContextHolder.locale)
    [error: "account.clusters.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleClusterNotFoundException(ClusterNotFoundException ex) {
    def message = messageSource.getMessage("cluster.not.found", [ex.cluster] as String[], "cluster.not.found", LocaleContextHolder.locale)
    [error: "cluster.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleServerGroupNotFoundException(ServerGroupNotFoundException ex) {
    def message = messageSource.getMessage("serverGroup.not.found", [ex.serverGroupName] as String[], "serverGroup.not.found", LocaleContextHolder.locale)
    [error: "serverGroup.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  static class AccountClustersNotFoundException extends RuntimeException {
    String application
    String account
  }

  static class ClusterNotFoundException extends RuntimeException {
    String cluster
  }

  static class ServerGroupNotFoundException extends RuntimeException {
    String serverGroupName
  }

  @Canonical
  static class ClusterViewModel {
    String name
    String account
    List<String> loadBalancers
    List<String> serverGroups
  }
}
