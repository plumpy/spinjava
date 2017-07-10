/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.front50.PipelineModelMutator;
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps pipeline configurations of concurrency settings, notifications,
 * triggers and parameters from the pipeline template schema to the original
 * non-templated pipeline schema.
 */
public class TemplatedPipelineModelMutator implements PipelineModelMutator {

  private final ObjectMapper pipelineTemplateObjectMapper;
  private final TemplateLoader templateLoader;
  private final Renderer renderer;

  public TemplatedPipelineModelMutator(ObjectMapper pipelineTemplateObjectMapper,
                                       TemplateLoader templateLoader,
                                       Renderer renderer) {
    this.pipelineTemplateObjectMapper = pipelineTemplateObjectMapper;
    this.templateLoader = templateLoader;
    this.renderer = renderer;
  }

  @Override
  public boolean supports(Map<String, Object> pipeline) {
    return "templatedPipeline".equals(pipeline.get("type")) && pipeline.containsKey("config");
  }

  @Override
  public void mutate(Map<String, Object> pipeline) {
    TemplateConfiguration configuration = pipelineTemplateObjectMapper.convertValue(pipeline.get("config"), TemplateConfiguration.class);

    PipelineTemplate template = null;

    // Dynamically sourced templates don't support configuration inheritance.
    if (!sourceContainsExpressions(configuration)) {
      template = getPipelineTemplate(configuration);
      applyConfigurationsFromTemplate(configuration.getConfiguration(), template.getConfiguration(), pipeline);
    }

    pipeline.computeIfAbsent("application", k -> configuration.getPipeline().getApplication());
    pipeline.computeIfAbsent("name", k -> configuration.getPipeline().getName());

    applyConfigurations(configuration.getConfiguration(), pipeline);
    renderConfigurations(pipeline, RenderUtil.createDefaultRenderContext(template, configuration, null));
  }

  private void applyConfigurationsFromTemplate(PipelineConfiguration configuration, Configuration templateConfiguration, Map<String, Object> pipeline) {
    if (configuration.getInherit().contains("concurrentExecutions") && templateConfiguration.getConcurrentExecutions() != null) {
      applyConcurrentExecutions(pipeline, templateConfiguration.getConcurrentExecutions());
    }
    if (configuration.getInherit().contains("triggers")) {
      pipeline.put("triggers", templateConfiguration.getTriggers());
    }
    if (configuration.getInherit().contains("parameters")) {
      pipeline.put("parameters", templateConfiguration.getParameters());
    }
    if (configuration.getInherit().contains("notifications")) {
      pipeline.put("notifications", templateConfiguration.getNotifications());
    }
  }

  @SuppressWarnings("unchecked")
  private void applyConfigurations(PipelineConfiguration configuration, Map<String, Object> pipeline) {
    if (configuration.getConcurrentExecutions() != null) {
      applyConcurrentExecutions(pipeline, configuration.getConcurrentExecutions());
    }
    if (!configuration.getTriggers().isEmpty()) {
      pipeline.put("triggers", TemplateMerge.mergeNamedContent((List<NamedHashMap>) pipeline.get("triggers"), configuration.getTriggers()));
    }
    if (!configuration.getParameters().isEmpty()) {
      pipeline.put("parameters", TemplateMerge.mergeNamedContent((List<NamedHashMap>) pipeline.get("parameters"), configuration.getParameters()));
    }
    if (!configuration.getNotifications().isEmpty()) {
      pipeline.put("notifications", TemplateMerge.mergeNamedContent((List<NamedHashMap>) pipeline.get("notifications"), configuration.getNotifications()));
    }
  }

  private void applyConcurrentExecutions(Map<String, Object> pipeline, Map<String, Object> concurrentExecutions) {
    if (concurrentExecutions.containsKey("limitConcurrent")) {
      pipeline.put("limitConcurrent", concurrentExecutions.get("limitConcurrent"));
    }
    if (concurrentExecutions.containsKey("keepWaitingPipelines")) {
      pipeline.put("keepWaitingPipelines", concurrentExecutions.get("keepWaitingPipelines"));
    }
    if (concurrentExecutions.containsKey("parallel")) {
      pipeline.put("parallel", concurrentExecutions.get("parallel"));
    }
  }

  @SuppressWarnings("unchecked")
  private void renderConfigurations(Map<String, Object> pipeline, RenderContext renderContext) {
    if (pipeline.containsKey("triggers")) {
      pipeline.put("triggers", renderList((List<Object>) pipeline.get("triggers"), renderContext));
    }
    if (pipeline.containsKey("parameters")) {
      pipeline.put("parameters", renderList((List<Object>) pipeline.get("parameters"), renderContext));
    }
    if (pipeline.containsKey("notifications")) {
      pipeline.put("notifications", renderList((List<Object>) pipeline.get("notifications"), renderContext));
    }
  }

  private List<Object> renderList(List<Object> list, RenderContext renderContext) {
    if (list == null) {
      return null;
    }
    return list.stream().map(i -> RenderUtil.deepRender(renderer, i, renderContext)).collect(Collectors.toList());
  }

  private boolean sourceContainsExpressions(TemplateConfiguration configuration) {
    String source = configuration.getPipeline().getTemplate().getSource();
    return source.contains("{{") || source.contains("{%");
  }

  private PipelineTemplate getPipelineTemplate(TemplateConfiguration configuration) {
    List<PipelineTemplate> templates = templateLoader.load(configuration.getPipeline().getTemplate());
    return TemplateMerge.merge(templates);
  }
}
