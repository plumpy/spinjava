/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.oort.cf.model

import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.ServerGroup
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.cloudfoundry.client.lib.domain.CloudService

/**
 * A Cloud Foundry application combined with its org/space coordinates.
 *
 * @author Greg Turnquist
 */
@CompileStatic
@EqualsAndHashCode(includes = ["name"])
class CloudFoundryServerGroup implements ServerGroup, Serializable {

  String name
  String type = 'cf'
  CloudApplication nativeApplication
  Boolean disabled = false
  Set<CloudFoundryApplicationInstance> instances = new HashSet<>()
  Set<String> services = new HashSet<>()
  Set<String> securityGroups = new HashSet<>()
  Map<String, Object> envVariables = new HashMap<>()
  Map<String, Object> cfSettings = new HashMap<>() // scaling, memory, etc.

  Set<CloudService> nativeServices = new HashSet<>()

  @Override
  String getRegion() {
    nativeApplication.space.organization.name
  }

  @Override
  Boolean isDisabled() {
    disabled
  }

  /**
   * Return an instance's "since" attribute if it exists. Otherwise, fallback to "updated"
   * @return
   */
  @Override
  Long getCreatedTime() {
    if (this.instances.size() > 0) {
      this.instances.first().launchTime
    } else {
      nativeApplication?.meta?.updated?.time
    }
  }

  @Override
  Set<String> getZones() {
    Collections.singleton(nativeApplication.space.name)
  }

  @Override
  Set<String> getLoadBalancers() {
    services
  }

  @Override
  Map<String, Object> getLaunchConfig() {
    envVariables
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Set<CloudFoundryApplicationInstance> instances = getInstances()
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
    return null
  }

  static Collection<Instance> filterInstancesByHealthState(Set<CloudFoundryApplicationInstance> instances,
                                                           HealthState healthState) {
    instances.findAll { CloudFoundryApplicationInstance it -> it.getHealthState() == healthState }
  }

}
