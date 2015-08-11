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


package com.netflix.spinnaker.internal.services

import com.netflix.spinnaker.gate.config.InsightConfiguration
import com.netflix.spinnaker.gate.services.InstanceService
import com.netflix.spinnaker.gate.services.internal.OortService
import spock.lang.Specification

class InstanceServiceSpec extends Specification {
  void "should include relevant insight actions for instance"() {
    given:
    def service = new InstanceService(
        oortService: Mock(OortService) {
          1 * getInstanceDetails(_, _, _) >> { return [privateIpAddress: "10.0.0.1", map: [:]] }
        },
        insightConfiguration: new InsightConfiguration(
            instance: [new InsightConfiguration.Link(url: '${account}-${region}-${instanceId}-{DNE}-${privateIpAddress}')]
        )
    )

    expect:
    service.getForAccountAndRegion("account", "region", "instanceId").insightActions*.url == [
        "account-region-instanceId-{DNE}-10.0.0.1"
    ]
  }

  void "should include account, region and instanceId in context"() {
    expect:
    InstanceService.getContext("prod", "us-west-1", "i-92431") == [
        "account": "prod", "region": "us-west-1", "instanceId": "i-92431"
    ]
  }
}
