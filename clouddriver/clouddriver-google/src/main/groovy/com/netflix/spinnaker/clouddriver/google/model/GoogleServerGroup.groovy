/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup

class GoogleServerGroup implements ServerGroup, Serializable {

  private static final String GOOGLE_SERVER_GROUP_TYPE = "gce"

  String name
  String region
  Set<String> zones = new HashSet<>()
  Set<GoogleInstance> instances = new HashSet<>()
  Set health = new HashSet<>()
  Map<String, Object> launchConfig
  Map<String, Object> asg
  Set<String> securityGroups
  Map buildInfo
  Boolean disabled = false

  private Map<String, Object> dynamicProperties = new HashMap<String, Object>()

  // Used as a deep copy-constructor.
  public static GoogleServerGroup newInstance(GoogleServerGroup originalGoogleServerGroup) {
    GoogleServerGroup copyGoogleServerGroup = new GoogleServerGroup()

    copyGoogleServerGroup.setDisabled(originalGoogleServerGroup.isDisabled())

    originalGoogleServerGroup.getMetaClass().getProperties().each { metaProperty ->
      def propertyName = metaProperty.name

      if (propertyName.equals("instances")) {
        originalGoogleServerGroup.instances.each { originalInstance ->
          copyGoogleServerGroup.instances << GoogleInstance.newInstance((GoogleInstance) originalInstance)
        }
      } else if (!["loadBalancers", "instanceCounts", "capacity"].contains(propertyName)) {
        // We only want to clone the properties that are not calculated on-demand.
        def valueCopy = Utils.getImmutableCopy(originalGoogleServerGroup.getProperty(propertyName))

        if (valueCopy) {
          copyGoogleServerGroup.setProperty(propertyName, valueCopy)
        }
      }
    }

    copyGoogleServerGroup
  }

  @JsonAnyGetter
  public Map<String, Object> anyProperty() {
    return dynamicProperties;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    dynamicProperties.put(name, value);
  }

  @Override
  String getType() {
    return GOOGLE_SERVER_GROUP_TYPE
  }

  @Override
  Boolean isDisabled() {
    return disabled
  }

  @Override
  Long getCreatedTime() {
    if (launchConfig) {
      return launchConfig.createdTime
    }
    return null
  }

  @Override
  Set<String> getLoadBalancers() {
    Set<String> loadBalancerNames = []
    if (asg && asg.containsKey("loadBalancerNames")) {
      loadBalancerNames = (Set<String>) asg.loadBalancerNames
    }
    return loadBalancerNames
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Set<Instance> instances = getInstances()
    new ServerGroup.InstanceCounts(
      total: instances.size(),
      up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
      down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
      unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
      starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0
    )
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    if (asg) {
      return new ServerGroup.Capacity(
        min: asg.minSize ? asg.minSize as Integer : 0,
        max: asg.maxSize ? asg.maxSize as Integer : 0,
        desired: asg.desiredCapacity ? asg.desiredCapacity as Integer : 0
      )
    }
    return null
  }

  @Override
  ServerGroup.ImageSummary getImageSummary() {
    def bi = buildInfo
    return new ServerGroup.ImageSummary() {
      String serverGroupName = name
      String imageName = launchConfig?.instanceTemplate?.name
      String imageId = launchConfig?.imageId

      @Override
      Map<String, Object> getBuildInfo() {
        return bi
      }

      @Override
      Map<String, Object> getImage() {
        return launchConfig?.instanceTemplate
      }
    }
  }

  static Collection<Instance> filterInstancesByHealthState(Set<Instance> instances, HealthState healthState) {
    instances.findAll { Instance it -> it.getHealthState() == healthState }
  }

}
