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
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.exceptions.ValidationException
import com.netflix.spinnaker.orca.clouddriver.service.JobService
import com.netflix.spinnaker.orca.exceptions.OperationFailedException
import com.netflix.spinnaker.orca.extensionpoint.pipeline.ExecutionPreprocessor
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.PipelineModelMutator
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Trigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateService
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import javassist.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError
import retrofit.http.Query

import javax.servlet.http.HttpServletResponse

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static net.logstash.logback.argument.StructuredArguments.value

@RestController
@Slf4j
class OperationsController {
  @Autowired
  ExecutionLauncher executionLauncher

  @Autowired(required = false)
  BuildService buildService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ExecutionRepository executionRepository

  @Autowired(required = false)
  PipelineTemplateService pipelineTemplateService

  @Autowired
  ContextParameterProcessor contextParameterProcessor

  @Autowired(required = false)
  List<ExecutionPreprocessor> executionPreprocessors = new ArrayList<>();

  @Autowired(required = false)
  private List<PipelineModelMutator> pipelineModelMutators = new ArrayList<>();

  @Autowired(required = false)
  WebhookService webhookService

  @Autowired(required = false)
  JobService jobService

  @Autowired(required = false)
  ArtifactResolver artifactResolver

  @Autowired
  FiatStatus fiatStatus

  @Autowired(required = false)
  FiatService fiatService

  @Autowired(required = false)
  Front50Service front50Service

  @RequestMapping(value = "/orchestrate", method = RequestMethod.POST)
  Map<String, Object> orchestrate(@RequestBody Map pipeline, HttpServletResponse response) {
    planOrOrchestratePipeline(pipeline)
  }

  @RequestMapping(value = "/orchestrate/{pipelineConfigId}", method = RequestMethod.POST)
  Map<String, Object> orchestratePipelineConfig(@PathVariable String pipelineConfigId, @RequestBody Map trigger) {
    Map pipelineConfig = buildPipelineConfig(pipelineConfigId, trigger)
    planOrOrchestratePipeline(pipelineConfig)
  }

  @RequestMapping(value = "/plan", method = RequestMethod.POST)
  Map<String, Object> plan(@RequestBody Map pipeline, @Query("resolveArtifacts") boolean resolveArtifacts, HttpServletResponse response) {
    planPipeline(pipeline, resolveArtifacts)
  }

  @RequestMapping(value = "/plan/{pipelineConfigId}", method = RequestMethod.POST)
  Map<String, Object> planPipelineConfig(@PathVariable String pipelineConfigId, @Query("resolveArtifacts") boolean resolveArtifacts, @RequestBody Map trigger) {
    Map pipelineConfig = buildPipelineConfig(pipelineConfigId, trigger)
    planPipeline(pipelineConfig, resolveArtifacts)
  }

  private Map buildPipelineConfig(String pipelineConfigId, Map trigger) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to retrieve pipeline configs. Fix this by setting front50.enabled: true")
    }

    List<Map> history = front50Service.getPipelineHistory(pipelineConfigId, 1)
    if (history.isEmpty()) {
      throw new NotFoundException("Pipeline config $pipelineConfigId not found")
    }
    Map pipelineConfig = history[0]
    pipelineConfig.trigger = trigger
    return pipelineConfig
  }

  private Map planOrOrchestratePipeline(Map pipeline) {
    if (pipeline.plan) {
      planPipeline(pipeline, false)
    } else {
      orchestratePipeline(pipeline)
    }
  }

  private Map<String, Object> planPipeline(Map pipeline, boolean resolveArtifacts) {
    log.info('Not starting pipeline (plan: true): {}', value("pipelineId", pipeline.id))
    pipelineModelMutators.stream().filter({m -> m.supports(pipeline)}).forEach({m -> m.mutate(pipeline)})
    return parseAndValidatePipeline(pipeline, resolveArtifacts)
  }

  private Map<String, Object> orchestratePipeline(Map pipeline) {
    def request = objectMapper.writeValueAsString(pipeline)

    Exception pipelineError = null
    try {
      pipeline = parseAndValidatePipeline(pipeline)
    } catch (Exception e) {
      pipelineError = e
    }

    def augmentedContext = [
      trigger: pipeline.trigger,
      templateVariables: pipeline.templateVariables ?: [:]
    ]
    def processedPipeline = contextParameterProcessor.process(pipeline, augmentedContext, false)
    processedPipeline.trigger = objectMapper.convertValue(processedPipeline.trigger, Trigger)

    if (pipelineError == null) {
      def id = startPipeline(processedPipeline)
      log.info("Started pipeline {} based on request body {}", id, request)
      return [ref: "/pipelines/" + id]
    } else {
      def id = markPipelineFailed(processedPipeline, pipelineError)
      log.info("Failed to start pipeline {} based on request body {}", id, request)
      throw pipelineError
    }
  }

  public Map parseAndValidatePipeline(Map pipeline) {
    return parseAndValidatePipeline(pipeline, true)
  }

  public Map parseAndValidatePipeline(Map pipeline, boolean resolveArtifacts) {
    parsePipelineTrigger(executionRepository, buildService, pipeline, resolveArtifacts)

    for (ExecutionPreprocessor preprocessor : executionPreprocessors.findAll {
      it.supports(pipeline, ExecutionPreprocessor.Type.PIPELINE)
    }) {
      pipeline = preprocessor.process(pipeline)
    }

    if (pipeline.disabled) {
      throw new InvalidRequestException("Pipeline is disabled and cannot be started.")
    }

    def linear = pipeline.stages.every { it.refId == null }
    if (linear) {
      applyStageRefIds(pipeline)
    }

    if (pipeline.errors != null) {
      throw new ValidationException("Pipeline template is invalid", pipeline.errors as List<Map<String, Object>>)
    }
    return pipeline
  }

  private void parsePipelineTrigger(ExecutionRepository executionRepository, BuildService buildService, Map pipeline, boolean resolveArtifacts) {
    if (!(pipeline.trigger instanceof Map)) {
      pipeline.trigger = [:]
      if (pipeline.plan && pipeline.type == "templatedPipeline" && pipelineTemplateService != null) {
        // If possible, initialize the config with a previous execution trigger context, to be able to resolve
        // dynamic parameters in jinja expressions
        try {
          def previousExecution = pipelineTemplateService.retrievePipelineOrNewestExecution(pipeline.executionId, pipeline.id)
          pipeline.trigger = objectMapper.convertValue(previousExecution.trigger, Map)
          pipeline.executionId = previousExecution.id
        } catch (ExecutionNotFoundException | IllegalArgumentException _) {
          log.info("Could not initialize pipeline template config from previous execution context.")
        }
      }
    }

    if (!pipeline.trigger.type) {
      pipeline.trigger.type = "manual"
    }

    if (!pipeline.trigger.user) {
      pipeline.trigger.user = AuthenticatedRequest.getSpinnakerUser().orElse("[anonymous]")
    }

    if (buildService) {
      decorateBuildInfo(pipeline.trigger)
    }

    if (pipeline.trigger.parentPipelineId && !pipeline.trigger.parentExecution) {
      Execution parentExecution
      try {
        parentExecution = executionRepository.retrieve(PIPELINE, pipeline.trigger.parentPipelineId)
      } catch (ExecutionNotFoundException e) {
        // ignore
      }

      if (parentExecution) {
        pipeline.trigger.isPipeline         = true
        pipeline.trigger.parentStatus       = parentExecution.status
        pipeline.trigger.parentExecution    = parentExecution
        pipeline.trigger.parentPipelineName = parentExecution.name

        pipeline.receivedArtifacts = artifactResolver.getAllArtifacts(parentExecution)
      }
    }

    if (!pipeline.trigger.parameters) {
      pipeline.trigger.parameters = [:]
    }

    if (pipeline.parameterConfig) {
      pipeline.parameterConfig.each {
        pipeline.trigger.parameters[it.name] = pipeline.trigger.parameters.containsKey(it.name) ? pipeline.trigger.parameters[it.name] : it.default
      }
    }

    if (resolveArtifacts) {
      artifactResolver?.resolveArtifacts(pipeline)
    }
  }

  @Deprecated
  private void decorateBuildInfo(Map trigger) {
    // Echo now adds build information to the trigger before sending it to Orca, and manual triggers now default to
    // going through echo (and thus receive build information). We still need this logic to populate build info for
    // manual triggers when the 'triggerViaEcho' deck feature flag is off, or to handle users still hitting the old
    // API endpoint manually, but we should short-circuit if we already have build info.
    if (trigger.master && trigger.job && trigger.buildNumber && !trigger.buildInfo) {
      log.info("Populating build information in Orca for trigger {}.", trigger)
      def buildInfo
      try {
        buildInfo = buildService.getBuild(trigger.buildNumber, trigger.master, trigger.job)
      } catch (RetrofitError e) {
        if (e.response?.status == 404) {
          throw new IllegalStateException("Build ${trigger.buildNumber} of ${trigger.master}/${trigger.job} not found")
        } else {
          throw new OperationFailedException("Failed to get build ${trigger.buildNumber} of ${trigger.master}/${trigger.job}", e)
        }
      }
      if (buildInfo?.artifacts) {
        if (trigger.type == "manual") {
          trigger.artifacts = buildInfo.artifacts
        }
      }
      trigger.buildInfo = buildInfo
      if (trigger.propertyFile) {
        try {
          trigger.properties = buildService.getPropertyFile(
            trigger.buildNumber as Integer,
            trigger.propertyFile as String,
            trigger.master as String,
            trigger.job as String
          )
        } catch (RetrofitError e) {
          if (e.response?.status == 404) {
            throw new IllegalStateException("Expected properties file " + trigger.propertyFile + " (configured on trigger), but it was missing")
          } else {
            throw new OperationFailedException("Failed to get properties file ${trigger.propertyFile}", e)
          }
        }
      }
    }
  }

  @RequestMapping(value = "/ops", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody List<Map> input) {
    def execution = [application: null, name: null, stages: input]
    parsePipelineTrigger(executionRepository, buildService, execution, true)
    startTask(execution)
  }

  @RequestMapping(value = "/ops", consumes = "application/context+json", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody Map input) {
    def execution = [application: input.application, name: input.description, stages: input.job, trigger: input.trigger ?: [:]]
    parsePipelineTrigger(executionRepository, buildService, execution, true)
    startTask(execution)
  }

  @RequestMapping(value = "/webhooks/preconfigured")
  List<Map<String, Object>> preconfiguredWebhooks() {
    if (!webhookService) {
      return []
    }
    def webhooks = webhookService.preconfiguredWebhooks

    if (webhooks && fiatStatus.isEnabled()) {
      if (webhooks.any { it.permissions }) {
        def userPermissionRoles = [new Role.View(new Role("anonymous"))] as Set<Role.View>
        try {
          String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
          UserPermission.View userPermission = fiatService.getUserPermission(user)
          userPermissionRoles = userPermission.roles
        } catch (Exception e) {
          log.error("Unable to determine roles for current user, falling back to 'anonymous'", e)
        }

        webhooks = webhooks.findAll { it.isAllowed("READ", userPermissionRoles) }
      }
    }

    return webhooks.collect {
      [ label: it.label,
        description: it.description,
        type: it.type,
        waitForCompletion: it.waitForCompletion,
        preconfiguredProperties: it.preconfiguredProperties,
        noUserConfigurableFields: it.noUserConfigurableFields(),
        parameters: it.parameters,
        parameterData: it.parameterData,
      ]
    }
  }

  @RequestMapping(value = "/jobs/preconfigured")
  List<Map<String, Object>> preconfiguredJob() {
    if (!jobService) {
      return []
    }
    return jobService?.getPreconfiguredStages().collect{
      [ label: it.label,
        description: it.description,
        type: it.type,
        waitForCompletion: it.waitForCompletion,
        noUserConfigurableFields: true,
        parameters: it.parameters,
        producesArtifacts: it.producesArtifacts,
      ]
    }
  }

  private static void applyStageRefIds(Map<String, Serializable> pipelineConfig) {
    def stages = (List<Map<String, Object>>) pipelineConfig.stages
    stages.eachWithIndex { Map<String, Object> stage, int index ->
      stage.put("refId", String.valueOf(index))
      if (index > 0) {
        stage.put("requisiteStageRefIds", Collections.singletonList(String.valueOf(index - 1)))
      } else {
        stage.put("requisiteStageRefIds", Collections.emptyList())
      }
    }
  }

  private String startPipeline(Map config) {
    injectPipelineOrigin(config)
    def json = objectMapper.writeValueAsString(config)
    def pipeline = executionLauncher.start(PIPELINE, json)
    return pipeline.id
  }

  private String markPipelineFailed(Map config, Exception e) {
    injectPipelineOrigin(config)
    def json = objectMapper.writeValueAsString(config)
    def pipeline = executionLauncher.fail(PIPELINE, json, e)
    return pipeline.id
  }

  private Map<String, String> startTask(Map config) {
    def linear = config.stages.every { it.refId == null }
    if (linear) {
      applyStageRefIds(config)
    }
    injectPipelineOrigin(config)

    for (ExecutionPreprocessor preprocessor : executionPreprocessors.findAll {
      it.supports(config, ExecutionPreprocessor.Type.ORCHESTRATION)
    }) {
      config = preprocessor.process(pipeline)
    }

    def json = objectMapper.writeValueAsString(config)
    log.info('requested task:{}', json)
    def pipeline = executionLauncher.start(ORCHESTRATION, json)
    [ref: "/tasks/${pipeline.id}".toString()]
  }

  private void injectPipelineOrigin(Map pipeline) {
    if (!pipeline.origin) {
      pipeline.origin = AuthenticatedRequest.spinnakerUserOrigin.orElse('unknown')
    }
  }
}
