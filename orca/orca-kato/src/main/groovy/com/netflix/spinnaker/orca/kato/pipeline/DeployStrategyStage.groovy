/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.kato.pipeline

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
abstract class DeployStrategyStage extends LinearStage {

  @Autowired OortService oort
  @Autowired ObjectMapper mapper
  @Autowired DisableAsgStage disableAsgStage
  @Autowired DestroyAsgStage destroyAsgStage

  DeployStrategyStage(String name) {
    super(name)
  }

  protected abstract List<Step> basicSteps()

  @CompileDynamic
  @Override
  protected List<Step> buildSteps(Stage stage) {
    Map cluster = stage.context.cluster as Map

    List<Step> steps
    if (cluster?.strategy == "redblack") {
      steps = redBlackSteps(stage)
    } else if (cluster?.strategy == "highlander") {
      steps = highlanderStages(stage)
    } else {
      steps = basicSteps()
    }
    steps.each {
      it.name = it.name?.replace(DisableAsgStage.MAYO_CONFIG_TYPE, MAYO_CONFIG_TYPE)?.replace(DestroyAsgStage.MAYO_CONFIG_TYPE, MAYO_CONFIG_TYPE)
    }
    steps
  }

  @VisibleForTesting
  protected List<Step> redBlackSteps(Stage stage) {
    def steps = basicSteps()

    def clusterConfig = ClusterConfig.fromContext(stage.context)
    def lastAsg = getLastAsg(clusterConfig.app, clusterConfig.account, clusterConfig.cluster)
    if (lastAsg) {
      def disableInputs = [asgName: lastAsg.name, regions: [lastAsg.region], credentials: clusterConfig.account]
      stage.context."disableAsg" = disableInputs
      steps.addAll(disableAsgStage.buildSteps(stage))
    }

    steps
  }

  @VisibleForTesting
  protected List<Step> highlanderStages(Stage stage) {
    def steps = basicSteps()

    def clusterConfig = ClusterConfig.fromContext(stage.context)
    def existingAsgs = getExistingAsgs(clusterConfig.app, clusterConfig.account, clusterConfig.cluster)
    if (existingAsgs) {
      def destroyAsgDescriptions = []
      for (asg in existingAsgs) {
        destroyAsgDescriptions << [asgName: asg.name, credentials: clusterConfig.account, regions: [asg.region]]
        steps.addAll(destroyAsgStage.buildSteps(stage))
      }
      stage.context."destroyAsgDescriptions" = destroyAsgDescriptions
    }

    steps
  }

  @CompileDynamic
  private Map getLastAsg(String app, String account, String cluster) {
    getExistingAsgs(app, account, cluster).sort { a, b -> b.name <=> a.name }?.getAt(0)
  }

  @CompileDynamic
  private List<Map> getExistingAsgs(String app, String account, String cluster) {
    try {
      def response = oort.getCluster(app, account, cluster)
      def json = response.body.in().text
      def map = mapper.readValue(json, Map)
      map.serverGroups
    } catch (e) {
      null
    }
  }

  @Immutable
  private static class ClusterConfig {
    String account
    String app
    String cluster

    @CompileDynamic
    static ClusterConfig fromContext(Map context) {
      String account = context.account
      String app = context.cluster.application
      String stack = context.cluster.stack
      String cluster = "$app${stack ? '-' + stack : ''}"

      new ClusterConfig(account, app, cluster)
    }
  }
}
