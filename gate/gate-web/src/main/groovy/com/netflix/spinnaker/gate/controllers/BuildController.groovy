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

import com.netflix.spinnaker.gate.services.BuildService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RequestMapping("/builds")
@RestController
class BuildController {
  @Autowired
  BuildService buildService

  @RequestMapping(method = RequestMethod.GET)
  List<String> getBuildMasters() {
    buildService.getBuildMasters()
  }

  @RequestMapping(value = "/{buildMaster}/jobs", method = RequestMethod.GET)
  List<String> getJobsForBuildMaster(@PathVariable("buildMaster") String buildMaster) {
    buildService.getJobsForBuildMaster(buildMaster)
  }

  @RequestMapping(value = "/{buildMaster}/jobs/{job:.+}", method = RequestMethod.GET)
  Map getJobConfig(@PathVariable("buildMaster") String buildMaster, @PathVariable("job") String job) {
    buildService.getJobConfig(buildMaster, job)
  }

  @RequestMapping(value = "/{buildMaster}/jobs/{job}/builds", method = RequestMethod.GET)
  List getBuilds(@PathVariable("buildMaster") String buildMaster, @PathVariable("job") String job) {
    buildService.getBuilds(buildMaster, job)
  }

  @RequestMapping(value = "/{buildMaster}/jobs/{job}/builds/{number}", method = RequestMethod.GET)
  Map getBuilds(@PathVariable("buildMaster") String buildMaster, @PathVariable("job") String job, @PathVariable("number") String number) {
    buildService.getBuild(buildMaster, job, number)
  }
}
