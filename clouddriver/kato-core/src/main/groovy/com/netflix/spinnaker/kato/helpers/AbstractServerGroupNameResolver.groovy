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

package com.netflix.spinnaker.kato.helpers
import com.netflix.frigga.Names
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder

/**
 * @author sthadeshwar
 */
abstract class AbstractServerGroupNameResolver {

  private final Boolean ignoreSequence

  AbstractServerGroupNameResolver() {
    this.ignoreSequence = false
  }

  AbstractServerGroupNameResolver(Boolean ignoreSequence) {
    this.ignoreSequence = ignoreSequence
  }

  abstract String getPreviousServerGroupName(String clusterName)

  String resolveNextServerGroupName(String application, String stack, String details) {
    Integer nextSequence = 0
    String expectedClusterName

    if (!stack && !details) {
      expectedClusterName = application
    } else if (!stack && details) {
      expectedClusterName = "${application}--${details}"
    } else if (stack && !details) {
      expectedClusterName = "${application}-${stack}"
    } else {
      expectedClusterName = "${application}-${stack}-${details}"
    }

    String previousServerGroupName = getPreviousServerGroupName(expectedClusterName)
    if (previousServerGroupName) {
      Names parts = Names.parseName(previousServerGroupName)
      nextSequence = ((parts.sequence ?: 0) + 1) % 1000
    }

    return generateServerGroupName(application, stack, details, nextSequence)
  }

  String generateServerGroupName(String application, String stack, String details, Integer nextSequence) {
    AutoScalingGroupNameBuilder builder = new AutoScalingGroupNameBuilder(
      appName: application, stack: stack, detail: details
    )
    String groupName = builder.buildGroupName(true)
    if (ignoreSequence) {
      return groupName
    }
    return String.format("%s-v%03d", groupName, nextSequence)
  }
}
