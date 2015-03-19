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

package com.netflix.spinnaker.orca.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.pipeline.OrchestrationStarter
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
class OperationsController {

  @Autowired
  PipelineStarter pipelineStarter

  @Autowired
  OrchestrationStarter orchestrationStarter

  @Autowired(required = false)
  IgorService igorService

  @Autowired
  ObjectMapper objectMapper

  @RequestMapping(value = "/orchestrate", method = RequestMethod.POST)
  Map<String, String> orchestrate(
    @RequestBody Map pipeline,
    @RequestParam(value = "user", required = false) String user,
    @RequestParam(value = "build.master", required = false) String master,
    @RequestParam(value = "build.job", required = false) String job,
    @RequestParam(value = "build.number", required = false) Integer buildNumber,
    @RequestParam(value = "build.propertyFile", required = false) String propertyFile) {

    if (!(pipeline.trigger instanceof Map)) {
      pipeline.trigger = [:]
    }
    pipeline.trigger = [type: "manual", user: user] + pipeline.trigger

    if (igorService) {
      if (master && job && buildNumber) {
        pipeline.trigger.job = job
        pipeline.trigger.master = master
        pipeline.trigger.buildNumber = buildNumber
        if (propertyFile) {
          pipeline.trigger.propertyFile = propertyFile
        }
      }

      getBuildInfo(pipeline.trigger)
    }
    startPipeline(pipeline)
  }

  private void getBuildInfo(Map trigger) {
    if (trigger.master && trigger.job && trigger.buildNumber) {
      trigger.buildInfo = igorService.getBuild(trigger.master, trigger.job, trigger.buildNumber)
      if (trigger.propertyFile) {
        trigger.properties = igorService.getPropertyFile(
          trigger.master as String,
          trigger.job as String,
          trigger.buildNumber as Integer,
          trigger.propertyFile as String
        )
      }
    }
  }

  @RequestMapping(value = "/ops", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody List<Map> input) {
    startTask([application: null, name: null, appConfig: null, stages: input])
  }

  @RequestMapping(value = "/ops", consumes = "application/context+json", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody Map input) {
    startTask([application: input.application, name: input.description, appConfig: input.appConfig, stages: input.job])
  }

  @RequestMapping(value = "/health", method = RequestMethod.GET)
  Boolean health() {
    true
  }

  private Map<String, String> startPipeline(Map config) {
    def json = objectMapper.writeValueAsString(config)
    def pipeline = pipelineStarter.start(json)
    [ref: "/pipelines/${pipeline.id}".toString()]
  }

  private Map<String, String> startTask(Map config) {
    def json = objectMapper.writeValueAsString(config)
    def pipeline = orchestrationStarter.start(json)
    [ref: "/tasks/${pipeline.id}".toString()]
  }
}
