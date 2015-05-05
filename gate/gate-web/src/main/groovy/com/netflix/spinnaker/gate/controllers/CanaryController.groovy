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

package com.netflix.spinnaker.gate.controllers
import com.netflix.spinnaker.gate.services.CanaryService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
/**
 *
 * @author sthadeshwar
 */
@RestController
@CompileStatic
class CanaryController {

  @Autowired
  CanaryService canaryService

  @RequestMapping(value = "/canaries/{id:.+}/generateCanaryResult", method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.CREATED)
  void generateCanaryResult(@PathVariable("id") String id,
                           @RequestParam("duration") int duration,
                           @RequestParam("durationUnit") String durationUnit) {
    canaryService.generateCanaryResult(id, duration, durationUnit)
  }

  @RequestMapping(value = "/canaryDeployments/{canaryDeploymentId}/canaryAnalysisHistory", method = RequestMethod.GET)
  List<Map> showCanaryAnalysisHistory(@PathVariable String canaryDeploymentId) {
    canaryService.getCanaryAnalysisHistory(canaryDeploymentId)
  }

  @RequestMapping(value = "/canaries/{id}", method = RequestMethod.GET)
  Map showCanary(@PathVariable String id) {
    canaryService.showCanary(id)
  }

  @RequestMapping(value = "/canaries/{id:.+}/overrideCanaryResult/{result}", method = RequestMethod.PUT)
  Map overrideCanaryResult(@PathVariable String id, @PathVariable String result, @RequestParam String reason) {
    canaryService.overrideCanaryResult(id, result, reason)
  }
}
