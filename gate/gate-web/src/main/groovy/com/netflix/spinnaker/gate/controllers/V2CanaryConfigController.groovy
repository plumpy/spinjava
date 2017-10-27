/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.CanaryConfigService
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v2/canaryConfig")
@ConditionalOnBean(CanaryConfigService)
class V2CanaryConfigController {

  @Autowired
  CanaryConfigService canaryConfigService

  @ApiOperation(value = "Retrieve a list of canary configurations")
  @RequestMapping(method = RequestMethod.GET)
  List getCanaryConfigs(@RequestParam(value = "application", required = false) String application) {
    canaryConfigService.getCanaryConfigs(application)
  }

  @ApiOperation(value = "Retrieve a canary configuration by id")
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  Map getCanaryConfig(@PathVariable String id) {
    canaryConfigService.getCanaryConfig(id)
  }

  @ApiOperation(value = "Create a canary configuration")
  @RequestMapping(method = RequestMethod.POST)
  Map createCanaryConfig(@RequestBody Map config) {
    [id: canaryConfigService.createCanaryConfig(config)]
  }

  @ApiOperation(value = "Update a canary configuration")
  @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
  Map updateCanaryConfig(@PathVariable String id, @RequestBody Map config) {
    [id: canaryConfigService.updateCanaryConfig(id, config)]
  }

  @ApiOperation(value = "Delete a canary configuration")
  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  void deleteCanaryConfig(@PathVariable String id) {
    canaryConfigService.deleteCanaryConfig(id)
  }
}
