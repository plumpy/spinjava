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

package com.netflix.spinnaker.mort.aws.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.mort.model.SecurityGroup
import com.netflix.spinnaker.mort.model.securitygroups.Rule
import groovy.transform.Immutable
import groovy.transform.Sortable

@Immutable
@Sortable(excludes = ['inboundRules', 'outboundRules'])
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class AmazonSecurityGroup implements SecurityGroup {
  final String type = "aws"
  final String id
  final String name
  final String vpcId
  final String description
  final String application
  final String accountName
  final String region
  final Set<Rule> inboundRules
  final Set<Rule> outboundRules
}
