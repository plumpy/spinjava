/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.titus

import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.AmazonServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class TitusServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  /**
   * Prefer composition over inheritance FTW!
   */
  @Delegate(excludes = ['getCloudProvider'])
  @Autowired
  AmazonServerGroupCreator delegate

  final String cloudProvider = "titus"

  String getCloudProvider() {
    return cloudProvider
  }

  Optional<String> getHealthProviderName() {
    return Optional.of("Titus")
  }
}
