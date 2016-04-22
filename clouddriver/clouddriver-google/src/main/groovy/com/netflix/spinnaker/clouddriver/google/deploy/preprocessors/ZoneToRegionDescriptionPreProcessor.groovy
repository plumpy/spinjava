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

package com.netflix.spinnaker.clouddriver.google.deploy.preprocessors

import com.netflix.spinnaker.clouddriver.google.deploy.description.AbandonAndDecrementGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.DestroyGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.EnableDisableGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.ModifyGoogleServerGroupInstanceTemplateDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.ResizeGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.TerminateAndDecrementGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleServerGroupTagsDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor
import org.springframework.stereotype.Component

@Component
class ZoneToRegionDescriptionPreProcessor implements AtomicOperationDescriptionPreProcessor {
  @Override
  boolean supports(Class descriptionClass) {
    return descriptionClass in [AbandonAndDecrementGoogleServerGroupDescription,
                                DestroyGoogleServerGroupDescription,
                                EnableDisableGoogleServerGroupDescription,
                                ModifyGoogleServerGroupInstanceTemplateDescription,
                                ResizeGoogleServerGroupDescription,
                                TerminateAndDecrementGoogleServerGroupDescription,
                                UpsertGoogleServerGroupTagsDescription]
  }

  @Override
  Map process(Map description) {
    description.with {
      if (!region && zone) {
        region = zone.substring(0, zone.lastIndexOf('-'))
      }

      // Only `region` should be propagated internally now.
      description.remove("zone")

      return description
    }
  }
}
