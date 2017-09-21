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
package com.netflix.spinnaker.orca.pipelinetemplate;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class TemplatedPipelineRequest {
  String id;
  String schema;
  String type;
  Map<String, Object> trigger;
  Map<String, Object> config;
  Map<String, Object> template;
  Boolean plan = false;
  boolean limitConcurrent = true;
  boolean keepWaitingPipelines = false;

  @JsonProperty("config")
  private void unpackConfig(Map<String, Object> config) {
    this.config = config;
    schema = (String) config.get("schema");
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public boolean isTemplatedPipelineRequest() {
    return "templatedPipeline".equals(type);
  }

  public Map<String, Object> getTrigger() {
    return trigger;
  }

  public void setTrigger(Map<String, Object> trigger) {
    this.trigger = trigger;
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

  public Map<String, Object> getTemplate() {
    return template;
  }

  public void setTemplate(Map<String, Object> template) {
    this.template = template;
  }

  public Boolean getPlan() {
    return plan;
  }

  public void setPlan(Boolean plan) {
    this.plan = plan;
  }

  public boolean isLimitConcurrent() {
    return limitConcurrent;
  }

  public boolean isKeepWaitingPipelines() {
    return keepWaitingPipelines;
  }
}
