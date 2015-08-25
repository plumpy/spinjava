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

import com.netflix.spinnaker.security.AuthenticatedRequest

import java.time.Clock
import com.netflix.spinnaker.orca.model.OrchestrationViewModel
import com.netflix.spinnaker.orca.pipeline.PipelineStartTracker
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import rx.schedulers.Schedulers

@RestController
class TaskController {
  @Autowired
  ExecutionRepository executionRepository

  @Autowired
  PipelineStartTracker startTracker

  @Value('${tasks.daysOfExecutionHistory:14}')
  int daysOfExecutionHistory

  @Value('${tasks.numberOfOldPipelineExecutionsToInclude:2}')
  int numberOfOldPipelineExecutionsToInclude

  Clock clock = Clock.systemUTC()

  @RequestMapping(value = "/applications/{application}/tasks", method = RequestMethod.GET)
  List<Orchestration> list(@PathVariable String application) {
    def startTimeCutoff = (new Date(clock.millis()) - daysOfExecutionHistory).time
    executionRepository.retrieveOrchestrationsForApplication(application)
      .filter({ Orchestration orchestration -> !orchestration.startTime || (orchestration.startTime > startTimeCutoff) })
      .map({ Orchestration orchestration -> convert(orchestration) })
      .subscribeOn(Schedulers.io()).toList().toBlocking().single().sort(startTimeOrId)
  }

  @RequestMapping(value = "/tasks", method = RequestMethod.GET)
  List<OrchestrationViewModel> list() {
    executionRepository.retrieveOrchestrations().toBlocking().iterator.collect { convert it }
  }

  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
  OrchestrationViewModel getTask(@PathVariable String id) {
    convert executionRepository.retrieveOrchestration(id)
  }

  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.DELETE)
  OrchestrationViewModel deleteTask(@PathVariable String id) {
    executionRepository.deleteOrchestration(id)
  }

  @RequestMapping(value = "/tasks/{id}/cancel", method = RequestMethod.PUT)
  OrchestrationViewModel cancelTask(@PathVariable String id) {
    def orchestration = executionRepository.retrieveOrchestration(id)
    orchestration.canceled = true
    executionRepository.store(orchestration)
    convert orchestration
  }

  @RequestMapping(value = "/pipelines/{id}", method = RequestMethod.GET)
  Pipeline getPipeline(@PathVariable String id) {
    executionRepository.retrievePipeline(id)
  }

  @RequestMapping(value = "/pipelines/{id}", method = RequestMethod.DELETE)
  void deletePipeline(@PathVariable String id) {
    executionRepository.deletePipeline(id)
  }

  @RequestMapping(value = "/pipelines/{id}/cancel", method = RequestMethod.PUT)
  Pipeline cancel(@PathVariable String id) {
    def pipeline = executionRepository.retrievePipeline(id)
    pipeline.canceled = true
    executionRepository.store(pipeline)
    pipeline
  }

  @RequestMapping(value = "/pipelines/running", method = RequestMethod.GET)
  List<String> runningPipelines() {
    startTracker.getAllStartedExecutions()
  }

  @RequestMapping(value = "/pipelines/waiting", method = RequestMethod.GET)
  List<String> waitingPipelines() {
    startTracker.getAllWaitingExecutions()
  }

  @RequestMapping(value = "/pipelines/{id}/stages/{stageId}", method = RequestMethod.PATCH)
  Pipeline updatePipelineStage(@PathVariable String id, @PathVariable String stageId, @RequestBody Map context) {
    def pipeline = executionRepository.retrievePipeline(id)
    def stage = pipeline.stages.find { it.id == stageId } as PipelineStage
    if (stage) {
      stage.context.putAll(context)
      stage.context["lastModifiedBy"] = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
      executionRepository.storeStage(stage)
    }
    pipeline
  }

  @RequestMapping(value = "/pipelines", method = RequestMethod.GET)
  List<Pipeline> getPipelines() {
    executionRepository.retrievePipelines().toBlocking().iterator.toList().sort { it.startTime ?: it.id }.reverse()
  }

  @RequestMapping(value = "/applications/{application}/pipelines", method = RequestMethod.GET)
  List<Pipeline> getApplicationPipelines(@PathVariable String application) {
    def pipelines = executionRepository.retrievePipelinesForApplication(application)
      .subscribeOn(Schedulers.io()).toList().toBlocking().single()

    def cutoffTime = (new Date(clock.millis()) - daysOfExecutionHistory).time

    def allPipelines = []
    pipelines.groupBy { it.pipelineConfigId }.values().each { List<Pipeline> pipelinesGroup ->
      def sortedPipelinesGroup = pipelinesGroup.sort(startTimeOrId).reverse()
      def recentPipelines = sortedPipelinesGroup.findAll { !it.startTime || it.startTime > cutoffTime }
      if (!recentPipelines && sortedPipelinesGroup) {
        // no pipeline executions within `daysOfExecutionHistory` so include the first `numberOfOldPipelineExecutionsToInclude`
        def upperBounds = Math.min(sortedPipelinesGroup.size(), numberOfOldPipelineExecutionsToInclude) - 1
        recentPipelines = sortedPipelinesGroup[0..upperBounds]
      }

      allPipelines.addAll(recentPipelines)
    }

    return allPipelines.sort(startTimeOrId)
  }

  private static Closure startTimeOrId = { a, b ->
    def aStartTime = a.startTime ?: 0
    def bStartTime = b.startTime ?: 0

    return aStartTime.compareTo(bStartTime) ?: b.id <=> a.id
  }

  private OrchestrationViewModel convert(Orchestration orchestration) {
    def variables = [:]
    for (stage in orchestration.stages) {
      for (entry in stage.context.entrySet()) {
        variables[entry.key] = entry.value
      }
    }
    new OrchestrationViewModel(
      id: orchestration.id,
      name: orchestration.description,
      status: orchestration.getStatus(),
      variables: variables.collect { key, value ->
        [
          "key"  : key,
          "value": value
        ]
      },
      steps: orchestration.stages.tasks.flatten(),
      buildTime: orchestration.buildTime,
      startTime: orchestration.startTime,
      endTime: orchestration.endTime,
      execution: orchestration
    )
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(ExecutionNotFoundException)
  void notFound() {}
}
