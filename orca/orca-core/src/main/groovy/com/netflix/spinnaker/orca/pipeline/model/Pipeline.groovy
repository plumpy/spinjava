/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.ExecutionStatus
import groovy.transform.CompileStatic


import static com.netflix.spinnaker.orca.ExecutionStatus.*

@CompileStatic
class Pipeline extends Execution {

  String application
  String name
  final Map<String, Object> trigger = [:]

  @JsonIgnore
  ExecutionStatus getStatus() {
    def status = stages.status.reverse().find {
      it != NOT_STARTED
    }

    if (!status) {
      NOT_STARTED
    } else if (status == SUCCEEDED && stages.last().status != SUCCEEDED) {
      RUNNING
    } else {
      status
    }
  }

  static Builder builder() {
    new Builder()
  }

  static class Builder {

    private final Pipeline pipeline = new Pipeline()

    Builder withTrigger(Map<String, Object> trigger = [:]) {
      if (trigger) {
        pipeline.@trigger.clear()
        pipeline.@trigger.putAll(trigger)
      }
      return this
    }

    Builder withStage(String type, Map<String, Object> context = [:]) {
      if (context.providerType) {
        type += "_$context.providerType"
      }

      pipeline.stages << new PipelineStage(pipeline, type, context)
      return this
    }

    Builder withStages(List<Map<String, Object>> stages) {
      stages.each {
        withStage(it.remove("type").toString(), it)
      }
      return this
    }

    Builder withStages(String... stageTypes) {
      stageTypes.each { String it ->
        withStage(it)
      }
      return this
    }

    Pipeline build() {
      pipeline
    }

    Builder withApplication(String application) {
      pipeline.@application = application
      return this
    }

    Builder withName(String name) {
      pipeline.@name = name
      return this
    }
  }

  ImmutablePipeline asImmutable() {
    def self = this
    new ImmutablePipeline() {
      @Override
      String getId() {
        self.id
      }

      @Override
      String getApplication() {
        self.application
      }

      @Override
      String getName() {
        self.name
      }

      @Override
      ImmutableMap<String, Object> getTrigger() {
        ImmutableMap.copyOf(self.trigger)
      }

      @Override
      ImmutableList<ImmutableStage> getStages() {
        ImmutableList.copyOf(self.stages.collect {
          it.asImmutable()
        })
      }

      @Override
      ExecutionStatus getStatus() {
        self.status
      }
    }
  }
}
