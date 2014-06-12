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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import reactor.event.Event

import static reactor.event.selector.Selectors.object

@CompileStatic
class LaunchConfigCachingAgent extends AbstractInfrastructureCachingAgent {

  LaunchConfigCachingAgent(AmazonNamedAccount account, String region) {
    super(account, region)
  }

  private Map<String, Integer> lastKnownLaunchConfigs = [:]

  void load() {
    log.info "$cachePrefix - Beginning Launch Config Cache Load."

    reactor.on(object("newLaunchConfig"), this.&loadNewLaunchConfig)

    def autoScaling = amazonClientProvider.getAutoScaling(account.credentials, region)
    def launchConfigs = autoScaling.describeLaunchConfigurations()
    def allLaunchConfigs = launchConfigs.launchConfigurations.collectEntries { LaunchConfiguration launchConfiguration -> [(launchConfiguration.launchConfigurationName): launchConfiguration] }
    Map<String, Integer> launchConfigsThisRun = (Map<String, Integer>)allLaunchConfigs.collectEntries { launchConfigName, launchConfig -> [(launchConfigName): launchConfig.hashCode()] }
    def newLaunchConfigs = launchConfigsThisRun.specialSubtract(lastKnownLaunchConfigs)
    def missingLaunchConfigs = lastKnownLaunchConfigs.keySet() - launchConfigsThisRun.keySet()

    if (newLaunchConfigs) {
      log.info "$cachePrefix - Loading ${newLaunchConfigs.size()} new or changedlaunch configs"
      for (launchConfigName in newLaunchConfigs.keySet()) {
        LaunchConfiguration launchConfig = (LaunchConfiguration)allLaunchConfigs[launchConfigName]
        reactor.notify("newLaunchConfig", Event.wrap(new NewLaunchConfigNotification(launchConfig, region)))
      }
    }
    if (missingLaunchConfigs) {
      log.info "$cachePrefix - Removing ${missingLaunchConfigs.size()} missing launch configs"
      for (launchConfigName in missingLaunchConfigs) {
        reactor.notify("missingLaunchConfig", Event.wrap(launchConfigName))
      }
    }
    if (!newLaunchConfigs && !missingLaunchConfigs) {
      log.info "$cachePrefix - Nothing to process"
    }

    lastKnownLaunchConfigs = launchConfigsThisRun
  }

  @Canonical
  static class NewLaunchConfigNotification {
    LaunchConfiguration launchConfiguration
    String region
  }

  void loadNewLaunchConfig(Event<NewLaunchConfigNotification> event) {
    def launchConfig = event.data.launchConfiguration
    def region = event.data.region
    cacheService.put(Keys.getLaunchConfigKey(launchConfig.launchConfigurationName, region), launchConfig)
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:cfg]"
  }
}
