/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.Job
import com.netflix.spinnaker.clouddriver.model.JobProvider
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/applications/{application}/jobs")
class JobController {

  @Autowired(required = false)
  List<JobProvider> jobProviders

  @Autowired
  MessageSource messageSource

  @ApiOperation(value = "Get a Job", notes = "Composed of many running `Process` objects")
  @RequestMapping(value = "/{account}/{location}/{id:.+}", method = RequestMethod.GET)
  Job getJob(@ApiParam(value = "Application name", required = true) @PathVariable String application,
             @ApiParam(value = "Account job was created by", required = true) @PathVariable String account,
             @ApiParam(value = "Namespace, region, or zone job is running in", required = true) @PathVariable String location,
             @ApiParam(value = "Unique identifier of job being looked up", required = true) @PathVariable String id) {
    Collection<Job> jobMatches = jobProviders.findResults {
      it.getJob(account, location, id)
    }
    if (!jobMatches) {
      throw new JobNotFoundException(name: id)
    }
    jobMatches.first()
  }

  @ApiOperation(value = "Get all Jobs in given application", notes = "Composed of many running `Process` objects")
  @RequestMapping(method = RequestMethod.GET)
  List<Job> getJobsByApp(@ApiParam(value = "Application name", required = true) @PathVariable String application,
                         @RequestParam(required = false, value = 'expand', defaultValue = 'false') String expand) {
    Collection<Job> jobMatches = jobProviders.collect {
      it.getJobsByApp(application)
    }.flatten()
    if (!jobMatches) {
      jobMatches = []
    }
    return jobMatches
  }

  static class JobNotFoundException extends RuntimeException {
    String name
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map jobNotFoundException(JobNotFoundException ex) {
    def message = messageSource.getMessage("job.not.found", [ex.name] as String[], "Job not found", LocaleContextHolder.locale)
    [error: "job.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map badRequestException(IllegalArgumentException ex) {
    [error: 'invalid.request', message: ex.message, status: HttpStatus.BAD_REQUEST]
  }
}
