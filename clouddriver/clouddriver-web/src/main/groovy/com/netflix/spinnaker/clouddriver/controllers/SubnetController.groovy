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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.Subnet
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/subnets")
@RestController
class SubnetController {

  @Autowired
  List<SubnetProvider> subnetProviders

  @RequestMapping(method = RequestMethod.GET)
  Set<Subnet> list() {
    subnetProviders.collectMany {
      it.all
    } as Set
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}")
  Set<Subnet> listByCloudProvider(@PathVariable String cloudProvider) {
    subnetProviders.findAll { subnetProvider ->
      subnetProvider.cloudProvider == cloudProvider
    } collectMany {
      it.all
    }
  }

  // TODO: implement the rest
}
