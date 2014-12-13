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


package com.netflix.spinnaker.gate.model.discovery

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.*

@CompileStatic
@Immutable
@EqualsAndHashCode(cache = true)
class DiscoveryInstance {
  public static final String HEALTH_TYPE = 'Discovery'

  public String getType() {
    HEALTH_TYPE
  }
  String hostName
  Integer port
  Integer securePort
  String application
  String ipAddress
  String status
  String overriddenStatus
  String state

  String availabilityZone
  String instanceId
  String amiId
  String instanceType

  String healthCheckUrl
  String vipAddress
  Long lastUpdatedTimestamp
  String asgName

  @JsonCreator
  public static DiscoveryInstance buildInstance(@JsonProperty('hostName') String hostName,
                                                @JsonProperty('port') Integer port,
                                                @JsonProperty('securePort') Integer securePort,
                                                @JsonProperty('app') String app,
                                                @JsonProperty('ipAddr') String ipAddr,
                                                @JsonProperty('status') String status,
                                                @JsonProperty('overriddenstatus') String overriddenstatus,
                                                @JsonProperty('dataCenterInfo') DataCenterInfo dataCenterInfo,
                                                @JsonProperty('healthCheckUrl') String healthCheckUrl,
                                                @JsonProperty('vipAddress') String vipAddress,
                                                @JsonProperty('lastUpdatedTimestamp') long lastUpdatedTimestamp,
                                                @JsonProperty('asgName') String asgName) {
    def meta = dataCenterInfo.metadata
    new DiscoveryInstance(
        hostName,
        port,
        securePort,
        app,
        ipAddr,
        status,
        overriddenstatus,
        status,
        meta?.availabilityZone,
        meta?.instanceId,
        meta?.amiId,
        meta?.instanceType,
        healthCheckUrl,
        vipAddress,
        lastUpdatedTimestamp,
        asgName)
  }
}


