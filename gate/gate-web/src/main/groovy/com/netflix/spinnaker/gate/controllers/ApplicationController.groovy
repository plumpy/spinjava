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

import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.ExecutionHistoryService
import com.netflix.spinnaker.gate.services.TaskService
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/applications")
@RestController
@Slf4j
class ApplicationController {

  static final String PIPELINE_EXECUTION_LIMIT = 'gate.execution.fetch.limit'

  @Autowired
  ApplicationService applicationService

  @Autowired
  ExecutionHistoryService executionHistoryService

  @Autowired
  TaskService taskService

  @Autowired
  PipelineController pipelineController

  @Autowired
  Environment environment

  @ApiOperation(value = "Retrieve a list of applications")
  @RequestMapping(method = RequestMethod.GET)
  List<Map> getAllApplications(
    @ApiParam(name = "account", required = false, value = "filters results to only include applications deployed in the specified account")
    @RequestParam(value = "account", required = false) String account,
    @ApiParam(name = "owner", required = false, value = "filteres results to only include applications owned by the specified email")
    @RequestParam(value = "owner", required = false) String owner) {
    return applicationService.getAllApplications()
      .findAll {
        if (!account) {
          return true
        }
        ((String) it.accounts ?: "").toLowerCase().split(",").contains(account.toLowerCase())
      }
      .findAll {
        if (!owner) {
          return true
        }
        ((String) it.email ?: "").toLowerCase() == owner.toLowerCase()
      }
  }

  @ApiOperation(value = "Retrieve an application's details")
  @RequestMapping(value = "/{application:.+}", method = RequestMethod.GET)
  Map getApplication(@PathVariable("application") String application, @RequestParam(value = "expand", defaultValue = "true") boolean expand) {
    def result = applicationService.getApplication(application, expand)
    if (!result) {
      log.warn("Application ${application} not found")
      throw new NotFoundException("Application not found (id: ${application})")
    } else if (!result.name) {
      // applicationService.getApplication() doesn't set the name unless clusters are found. Deck requires the name.
      result.name = application
    }
    result
  }

  @ApiOperation(value = "Retrieve a list of an application's configuration revision history")
  @RequestMapping(value = "/{application}/history", method = RequestMethod.GET)
  List<Map> getApplicationHistory(@PathVariable("application") String application,
                                  @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return applicationService.getApplicationHistory(application, limit)
  }

  @ApiOperation(value = "Retrieve a list of an application's tasks")
  @RequestMapping(value = "/{application}/tasks", method = RequestMethod.GET)
  List getTasks(@PathVariable("application") String application,
                @RequestParam(value = "limit", required = false) Integer limit,
                @RequestParam(value = "statuses", required = false) String statuses) {
    executionHistoryService.getTasks(application, limit, statuses)
  }

  @ApiOperation(value = "Retrieve a list of an application's pipeline executions")
  @RequestMapping(value = "/{application}/pipelines", method = RequestMethod.GET)
  List getPipelines(@PathVariable("application") String application,
                    @RequestParam(value = "limit", required = false) Integer limit,
                    @RequestParam(value = "statuses", required = false) String statuses) {
    def listLimit = limit ?: environment.getProperty(PIPELINE_EXECUTION_LIMIT, Integer, 10)
    executionHistoryService.getPipelines(application, listLimit, statuses)
  }

  /**
   * @deprecated There is no reason to provide an app name, use PipelineController instead for pipeline operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/pipelines/{id}/cancel", method = RequestMethod.PUT)
  Map cancelPipeline(@PathVariable("id") String id,
                     @RequestParam(required = false) String reason) {
    taskService.cancelPipeline(id, reason)
  }

  @ApiOperation(value = "Retrieve a list of an application's pipeline configurations")
  @RequestMapping(value = "/{application}/pipelineConfigs", method = RequestMethod.GET)
  List getPipelineConfigsForApplication(@PathVariable("application") String application) {
    applicationService.getPipelineConfigsForApplication(application)
  }

  @ApiOperation(value = "Retrieve a pipeline configuration")
  @RequestMapping(value = "/{application}/pipelineConfigs/{pipelineName:.+}", method = RequestMethod.GET)
  Map getPipelineConfig(
    @PathVariable("application") String application, @PathVariable("pipelineName") String pipelineName) {
    applicationService.getPipelineConfigsForApplication(application).find {
      it.name == pipelineName
    }
  }

  @ApiOperation(value = "Retrieve a list of an application's pipeline strategy configurations")
  @RequestMapping(value = "/{application}/strategyConfigs", method = RequestMethod.GET)
  List getStrategyConfigsForApplication(@PathVariable("application") String application) {
    applicationService.getStrategyConfigsForApplication(application)
  }

  @ApiOperation(value = "Retrieve a pipeline strategy configuration")
  @RequestMapping(value = "/{application}/strategyConfigs/{strategyName}", method = RequestMethod.GET)
  Map getStrategyConfig(@PathVariable("application") String application,
                        @PathVariable("strategyName") String strategyName) {
    applicationService.getStrategyConfigsForApplication(application).find {
      it.name == strategyName
    }
  }

  /**
   * @deprecated Use PipelineController instead for pipeline operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/pipelineConfigs/{pipelineName:.+}", method = RequestMethod.POST)
  HttpEntity invokePipelineConfig(@PathVariable("application") String application,
                                  @PathVariable("pipelineName") String pipelineName,
                                  @RequestBody(required = false) Map trigger,
                                  @RequestParam(required = false, value = "user") String user) {
    return pipelineController.invokePipelineConfig(application, pipelineName, trigger)
  }

  /**
   * @deprecated There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/tasks/{id}", method = RequestMethod.GET)
  Map getTask(@PathVariable("id") String id) {
    taskService.getTask(id)
  }

  /**
   * @deprecated There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/tasks/{id}/cancel", method = RequestMethod.PUT)
  Map cancelTask(@PathVariable("id") String id) {
    taskService.cancelTask(id)
  }

  /**
   * @deprecated There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/tasks/{id}/details/{taskDetailsId}", method = RequestMethod.GET)
  Map getTaskDetails(@PathVariable("id") String id,
                     @PathVariable("taskDetailsId") String taskDetailsId,
                     @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    taskService.getTaskDetails(taskDetailsId, sourceApp)
  }

  /**
   * @deprecated There is no reason to provide an app name, use TaskController instead for task operations.
   */
  @Deprecated
  @RequestMapping(value = "/{application}/tasks", method = RequestMethod.POST)
  Map task(@PathVariable String application, @RequestBody Map map) {
    taskService.createAppTask(application, map)
  }

  static class BakeCommand {
    String pkg
    String baseOs = "ubuntu"
    String baseLabel = "release"
    String region = "us-east-1"
  }
}
