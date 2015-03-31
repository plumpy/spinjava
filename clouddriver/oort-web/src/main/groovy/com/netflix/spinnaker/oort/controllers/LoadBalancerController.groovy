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

package com.netflix.spinnaker.oort.controllers

import com.netflix.spinnaker.oort.model.LoadBalancer
import com.netflix.spinnaker.oort.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/applications/{application}/clusters/{account}/{cluster}/{type}/loadBalancers")
class LoadBalancerController {

  @Autowired
  List<LoadBalancerProvider> loadBalancerProviders

  @RequestMapping(method = RequestMethod.GET)
  Set<LoadBalancer> list(@PathVariable String account, @PathVariable String cluster, @PathVariable String type) {
    ((List<LoadBalancer>) loadBalancerProviders.collectMany {
      it.getLoadBalancers(account, cluster, type) ?: []
    }).sort { a, b -> a.name.toLowerCase() <=> b.name.toLowerCase() }
  }

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  Set<LoadBalancer> list(@PathVariable String account, @PathVariable String cluster, @PathVariable String type, @PathVariable String name) {
    ((List<LoadBalancer>) loadBalancerProviders.collectMany {
      it.getLoadBalancer(account, cluster, type, name) ?: []
    }).sort { a, b -> a.name.toLowerCase() <=> b.name.toLowerCase() }
  }

  @RequestMapping(value = "/{name}/{region}", method = RequestMethod.GET)
  LoadBalancer list(@PathVariable String account, @PathVariable String cluster, @PathVariable String type, @PathVariable String loadBalancerName, @PathVariable String region, HttpServletResponse response) {
    def lb = (LoadBalancer)loadBalancerProviders.collect {
      it.getLoadBalancer(account, cluster, type, loadBalancerName, region) ?: []
    }?.getAt(0)
    if (lb) {
      lb
    } else {
      response.status = 404
    }
  }
}
