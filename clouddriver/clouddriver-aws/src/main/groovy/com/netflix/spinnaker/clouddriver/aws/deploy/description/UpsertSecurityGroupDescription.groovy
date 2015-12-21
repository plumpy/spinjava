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

package com.netflix.spinnaker.clouddriver.aws.deploy.description

import groovy.transform.Canonical

class UpsertSecurityGroupDescription extends AbstractAmazonCredentialsDescription {
  String name
  String description
  String vpcId
  String region

  List<SecurityGroupIngress> securityGroupIngress
  List<IpIngress> ipIngress

  boolean ingressAppendOnly = false

  static abstract class Ingress {
    Integer startPort
    Integer endPort
    String ipProtocol

    @Deprecated void setType(String ipProtocol) {
      this.ipProtocol = ipProtocol
    }
  }

  @Canonical
  static class SecurityGroupIngress extends Ingress {
    String accountName

    String id

    String vpcId
    String name

    String toString() {
      "${accountName ?: ''}.${vpcId ?: ''}.${name ?: id ?: ''}"
    }
  }

  static class IpIngress extends Ingress {
    String cidr
  }
}
