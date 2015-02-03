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


package com.netflix.spinnaker.kato.aws.deploy.ops.discovery

import com.netflix.frigga.Names
import com.netflix.spinnaker.kato.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.kato.data.task.Task
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class DiscoverySupport {
  private static final long THROTTLE_MS = 150

  @Autowired
  RestTemplate restTemplate

  void updateDiscoveryStatusForInstances(AbstractAmazonCredentialsDescription description,
                                         Task task,
                                         String phaseName,
                                         String region,
                                         DiscoveryStatus discoveryStatus,
                                         List<String> instanceIds) {
    if (!description.credentials.discoveryEnabled) {
      throw new DiscoveryNotConfiguredException()
    }

    def discovery = String.format(description.credentials.discovery, region)

    instanceIds.eachWithIndex { instanceId, index ->
      if (index > 0) {
        sleep THROTTLE_MS
      }

      if (!task.status.isFailed()) {
        task.updateStatus phaseName, "Attempting to mark ${instanceId} as '${discoveryStatus.value}' in discovery."
        def instanceDetails = restTemplate.getForEntity("${discovery}/v2/instances/${instanceId}", Map)
        def applicationName = instanceDetails?.body?.instance?.app
        if (applicationName) {
          restTemplate.put("${discovery}/v2/apps/${applicationName}/${instanceId}/status?value=${discoveryStatus.value}", [:])
          task.updateStatus phaseName, "Marked ${instanceId} as '${discoveryStatus.value}' in discovery."
        } else {
          task.updateStatus phaseName, "Instance '${instanceId}' does not exist in discovery, unable to mark as '${discoveryStatus.value}'"
          task.fail()
        }
      }
    }
  }

  enum DiscoveryStatus {
    Enable('UP'),
    Disable('OUT_OF_SERVICE')

    String value

    DiscoveryStatus(String value) {
      this.value = value
    }
  }

  @InheritConstructors
  static class DiscoveryNotConfiguredException extends RuntimeException {}
}
