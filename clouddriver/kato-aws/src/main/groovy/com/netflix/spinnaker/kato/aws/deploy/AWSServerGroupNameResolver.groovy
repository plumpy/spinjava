/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kato.aws.deploy
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory.RegionScopedProvider
import com.netflix.spinnaker.kato.helpers.AbstractServerGroupNameResolver
import groovy.transform.CompileStatic
/**
 * @author sthadeshwar
 */
@CompileStatic
class AWSServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private final RegionScopedProvider regionScopedProvider

  AWSServerGroupNameResolver(RegionScopedProvider regionScopedProvider) {
    this.regionScopedProvider = regionScopedProvider
  }

  @Override
  String getPreviousServerGroupName(String clusterName) {
    AsgService asgService = regionScopedProvider.asgService
    AutoScalingGroup ancestorAsg = asgService.getAncestorAsg(clusterName)
    return ancestorAsg ? ancestorAsg.autoScalingGroupName : null
  }
}
