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


package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.StrategyService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@Slf4j
@CompileStatic
@RestController
@RequestMapping("/strategies")
class StrategyController {
  @Autowired
  StrategyService strategyService

  @RequestMapping(value = "/{application}/{strategyName:.+}", method = RequestMethod.DELETE)
  void deleteStrategy(@PathVariable String application, @PathVariable String strategyName) {
    strategyService.deleteForApplication(application, strategyName)
  }

  @RequestMapping(value = '', method = RequestMethod.POST)
  void saveStrategy(@RequestBody Map strategy) {
    strategyService.save(strategy)
  }

  @RequestMapping(value = 'move', method = RequestMethod.POST)
  void renameStrategy(@RequestBody Map renameCommand) {
    strategyService.move(renameCommand)
  }

  @RequestMapping(value = "{id}", method = RequestMethod.PUT)
  Map updateStrategy(@PathVariable("id") String id, @RequestBody Map strategy) {
    strategyService.update(id, strategy)
  }
}
