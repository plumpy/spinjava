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

package com.netflix.spinnaker.gate.retrofit
import com.netflix.spinnaker.gate.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.gate.services.EurekaLookupService
import org.springframework.beans.factory.annotation.Autowired
import retrofit.client.OkClient
import retrofit.client.Request

import java.util.regex.Pattern

class EurekaOkClient extends OkClient {
  private static final Pattern NIWS_SCHEME_PATTERN = ~("niws://([^/]+)(.*)")

  @Autowired
  EurekaLookupService eureka

  @Override
  protected HttpURLConnection openConnection(Request r) throws IOException {
    def request = r
    if (request.url ==~ NIWS_SCHEME_PATTERN) {
      def matcher = request.url =~ NIWS_SCHEME_PATTERN
      String vip = matcher[0][1]
      String path = ""
      if (matcher[0].size() > 2) {
        path = matcher[0][2]
      }
      def apps = eureka.getApplications(vip)
      def randomInstance = DiscoveryApplication.getRandomUpInstance(apps)
      if (!randomInstance) {
        throw new RuntimeException("Error resolving Eureka UP instance for ${vip}!")
      } else {
        request = new Request(r.method, "http://${randomInstance.hostName}:${randomInstance.port.port}${path}", r.headers, r.body)
      }
    }
    super.openConnection(request)
  }

}
