/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.domain

import groovy.transform.AutoClone
import groovy.transform.Canonical
import org.openstack4j.model.network.ext.LbPool

@AutoClone
@Canonical
class LoadBalancerPool implements LoadBalancerResolver {
  String id
  String name
  LoadBalancerProtocol protocol
  LoadBalancerMethod method
  String subnetId
  Integer internalPort
  String description

  void setInternalPort(Integer port) {
    this.internalPort = port
    this.description = "internal_port=${internalPort}"
  }

  boolean doesMethodMatch(String methodName) {
    method?.name() == methodName
  }

  boolean doesInternalPortMatch(String currentDescription) {
    currentDescription != null && this.internalPort == getInternalPort(currentDescription)
  }

  boolean equals(LbPool lbPool) {
    doesMethodMatch(lbPool.lbMethod?.name()) && this.name == lbPool.name && doesInternalPortMatch(lbPool.description)
  }
}
