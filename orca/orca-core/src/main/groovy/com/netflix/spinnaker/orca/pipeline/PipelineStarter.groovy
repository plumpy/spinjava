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

package com.netflix.spinnaker.orca.pipeline

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import com.google.common.annotations.VisibleForTesting
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep

@Component
@CompileStatic
class PipelineStarter extends AbstractOrchestrationInitiator<Pipeline> {

  @Override
  protected Pipeline createSubject(Map<String, Object> config) {
    def pipeline = parseConfig(config)
    pipelineStore.store(pipeline)
    return pipeline
  }

  /**
   * Builds a _pipeline_ based on config from _Mayo_.
   *
   * @param configJson _Mayo_ pipeline configuration.
   * @return the pipeline that was created.
   */
  protected Job build(Map<String, Object> config, Pipeline subject) {
    createJobFrom(subject)
  }

  @VisibleForTesting
  @PackageScope
  static Pipeline parseConfig(Map<String, Object> config) {
    Pipeline.builder()
            .withApplication(config.application.toString())
            .withName(config.name.toString())
            .withTrigger((Map<String, Serializable>) config.trigger)
            .withStages((List<Map<String, Serializable>>) config.stages)
            .build()
  }

  private Job createJobFrom(Pipeline pipeline) {
    def jobBuilder = jobs.get("Pipeline:${pipeline.application}:${pipeline.name}:${pipeline.id}")
                         .flow(initializationStep(steps, pipeline)) as JobFlowBuilder
    buildFlow(jobBuilder, pipeline).build().build()
  }

  private JobFlowBuilder buildFlow(JobFlowBuilder jobBuilder, Pipeline pipeline) {
    (JobFlowBuilder) pipeline.stages.inject(jobBuilder, this.&createStage)
  }

}
