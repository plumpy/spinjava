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

package com.netflix.spinnaker.internal.controllers

import com.netflix.spinnaker.gate.services.ElasticIpService
import com.netflix.spinnaker.internal.services.ElasticIpService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RequestMapping("/elasticIps/{account}")
@RestController
class ElasticIpController {
  @Autowired
  ElasticIpService elasticIpService

  @RequestMapping(method = RequestMethod.GET)
  List<Map> getAllForAccount(@PathVariable("account") String account) {
    return elasticIpService.getForAccount(account)
  }

  @RequestMapping(value="/{region}", method = RequestMethod.GET)
  List<Map> getAllForAccountAndRegion(@PathVariable("account") String account,
                                      @PathVariable("region") String region) {
    return elasticIpService.getForAccountAndRegion(account, region)
  }
}
