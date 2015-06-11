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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.kato.pipeline.DestroyAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.DisableAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.ModifyAsgLaunchConfigurationStage
import com.netflix.spinnaker.orca.kato.pipeline.ModifyScalingProcessStage
import com.netflix.spinnaker.orca.kato.pipeline.ResizeAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.RollingPushStage
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.pipeline.LinearStage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
abstract class DeployStrategyStage extends LinearStage {
  Logger logger = LoggerFactory.getLogger(DeployStrategyStage)

  @Autowired ObjectMapper mapper
  @Autowired ResizeAsgStage resizeAsgStage
  @Autowired DisableAsgStage disableAsgStage
  @Autowired DestroyAsgStage destroyAsgStage
  @Autowired ModifyScalingProcessStage modifyScalingProcessStage
  @Autowired SourceResolver sourceResolver
  @Autowired ModifyAsgLaunchConfigurationStage modifyAsgLaunchConfigurationStage
  @Autowired RollingPushStage rollingPushStage

  DeployStrategyStage(String name) {
    super(name)
  }

  /**
   * @return the steps for the stage excluding whatever cleanup steps will be
   * handled by the deployment strategy.
   */
  protected abstract List<Step> basicSteps(Stage stage)

  /**
   * @param stage the stage configuration.
   * @return the details of the cluster that you are deploying to.
   */
  protected CleanupConfig determineClusterForCleanup(Stage stage) {
    def stageData = stage.mapTo(StageData)
    new CleanupConfig(stageData.account, stageData.cluster, stageData.availabilityZones.keySet().toList())
  }

  /**
   * @param stage the stage configuration.
   * @return the strategy parameter.
   */
  protected Strategy strategy(Stage stage) {
    def stageData = stage.mapTo(StageData)
    Strategy.fromStrategy(stageData.strategy)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    correctContext(stage)
    def strategy = strategy(stage)
    strategy.composeFlow(this, stage)

    List<Step> steps = [buildStep(stage, "determineSourceServerGroup", DetermineSourceServerGroupTask)]
    if (!strategy.replacesBasicSteps()) {
      steps.addAll((basicSteps(stage) ?: []) as List<Step>)
    }
    return steps
  }

  /**
   * This nasty method is here because of an unfortunate misstep in pipeline configuration that introduced a nested
   * "cluster" key, when in reality we want all of the parameters to be derived from the top level. To preserve
   * functionality (and not break any contracts), this method is employed to move the nested data back to the context's
   * top-level
   */
  private static void correctContext(Stage stage) {
    if (stage.context.containsKey("cluster")) {
      stage.context.putAll(stage.context.cluster as Map)
    }
    stage.context.remove("cluster")
  }

  @VisibleForTesting
  @CompileDynamic
  protected void composeRedBlackFlow(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def cleanupConfig = determineClusterForCleanup(stage)
    def existingAsgs = sourceResolver.getExistingAsgs(
      stageData.application, cleanupConfig.account, cleanupConfig.cluster, stageData.providerType
    )

    if (existingAsgs) {
      for (entry in stageData.availabilityZones) {
        def region = entry.key
        if (!cleanupConfig.regions.contains(region)) {
          continue
        }
        def asgs = existingAsgs.findAll { it.region == region }.collect { it.name }
        def latestAsg = asgs.size() > 0 ? asgs?.last() : null

        if (!latestAsg) {
          continue
        }
        def nextStageContext = [asgName: latestAsg, regions: [region], credentials: cleanupConfig.account]

        if (nextStageContext.asgName) {
          def names = Names.parseName(nextStageContext.asgName as String)
          if (stageData.application != names.app) {
            logger.info("Next stage context targeting application not belonging to the source stage! ${mapper.writeValueAsString(nextStageContext)}")
            continue
          }
        }
        if (stageData.scaleDown) {
          nextStageContext.capacity = [min: 0, max: 0, desired: 0]
          injectAfter(stage, "scaleDown", resizeAsgStage, nextStageContext)
        }
        injectAfter(stage, "disable", disableAsgStage, nextStageContext)
        // delete the oldest asgs until there are maxRemainingAsgs left (including the newly created one)
        if (stageData?.maxRemainingAsgs > 0 && (asgs.size() - stageData.maxRemainingAsgs) >= 0) {
          asgs[0..(asgs.size() - stageData.maxRemainingAsgs)].each { asg ->
            logger.info("Injecting destroyAsg stage (${region}:${asg})")
            nextStageContext.putAll([asgName: asg, credentials: cleanupConfig.account, regions: [region]])
            injectAfter(stage, "destroyAsg", destroyAsgStage, nextStageContext)
          }
        }
      }
    }
  }

  protected void composeRollingPushFlow(Stage stage) {
    def source = sourceResolver.getSource(stage)

    def modifyCtx = stage.context + [
      region: source.region,
      regions: [source.region],
      asgName: source.asgName,
      'deploy.server.groups': [(source.region): [source.asgName]],
      useSourceCapacity: true,
      credentials: source.account,
      source: [
        asgName: source.asgName,
        account: source.account,
        region: source.region,
        useSourceCapacity: true
      ]
    ]

    injectAfter(stage, "modifyLaunchConfiguration", modifyAsgLaunchConfigurationStage, modifyCtx)
    injectAfter(stage, "rollingPush", rollingPushStage, modifyCtx)
  }

  @CompileDynamic
  protected void composeHighlanderFlow(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def cleanupConfig = determineClusterForCleanup(stage)
    def existingAsgs = sourceResolver.getExistingAsgs(
      stageData.application, cleanupConfig.account, cleanupConfig.cluster, stageData.providerType
    )
    if (existingAsgs) {
      for (entry in stageData.availabilityZones) {
        def region = entry.key
        if (!cleanupConfig.regions.contains(region)) {
          continue
        }

        existingAsgs.findAll { it.region == region }.each { Map asg ->
          def nextContext = [asgName: asg.name, credentials: cleanupConfig.account, regions: [region]]
          if (nextContext.asgName) {
            def names = Names.parseName(nextContext.asgName as String)
            if (stageData.application != names.app) {
              logger.info("Next stage context targeting application not belonging to the source stage! ${mapper.writeValueAsString(nextContext)}")
              return
            }
          }

          logger.info("Injecting destroyAsg stage (${asg.region}:${asg.name})")
          injectAfter(stage, "destroyAsg", destroyAsgStage, nextContext)
        }
      }
    }
  }

  @Immutable
  static class CleanupConfig {
    String account
    String cluster
    List<String> regions
  }
}
