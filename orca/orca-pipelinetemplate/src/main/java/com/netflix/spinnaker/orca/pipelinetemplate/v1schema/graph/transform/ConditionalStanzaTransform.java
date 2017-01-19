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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.Conditional;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;

import java.util.List;

public class ConditionalStanzaTransform implements PipelineTemplateVisitor {

  private TemplateConfiguration templateConfiguration;

  private Renderer renderer;

  public ConditionalStanzaTransform(TemplateConfiguration templateConfiguration, Renderer renderer) {
    this.templateConfiguration = templateConfiguration;
    this.renderer = renderer;
  }

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    trimConditionals(pipelineTemplate.getStages(), pipelineTemplate);
    trimConditionals(templateConfiguration.getStages(), pipelineTemplate);
  }

  private <T extends Conditional> void trimConditionals(List<T> list, PipelineTemplate template) {
    if (list == null) {
      return;
    }

    for (T el : list) {
      if (el.getWhen() == null || el.getWhen().size() == 0) {
        continue;
      }

      RenderContext context = new RenderContext(templateConfiguration.getPipeline().getApplication(), template);
      context.putAll(templateConfiguration.getPipeline().getVariables());

      for (String conditional : el.getWhen()) {
        String rendered = renderer.render(conditional, context);
        if (!Boolean.parseBoolean(rendered)) {
          list.remove(el);
          return;
        }
      }
    }
  }
}
