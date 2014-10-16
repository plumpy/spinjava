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
import javax.annotation.PostConstruct
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet
import com.netflix.spinnaker.orca.batch.StageBuilder
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
@CompileStatic
class PipelineStarter {

  @Autowired private ApplicationContext applicationContext
  @Autowired private JobLauncher launcher
  @Autowired private JobBuilderFactory jobs
  @Autowired private StepBuilderFactory steps
  @Autowired private ObjectMapper mapper

  private final Map<String, StageBuilder> stages = [:]

  /**
   * Builds and launches a _pipeline_ based on config from _Mayo_.
   *
   * @param configJson _Mayo_ pipeline configuration.
   * @return the pipeline that was created.
   */
  Pipeline start(String configJson) {
    def pipeline = parseConfig(configJson)
    def job = createJobFrom(pipeline)
    launcher.run(job, new JobParameters())
    // TODO: update the id
    return pipeline
  }

  @PostConstruct
  void initialize() {
    applicationContext.getBeansOfType(StageBuilder).values().each {
      stages[it.name] = it
    }
    applicationContext.getBeansOfType(StandaloneTask).values().each {
      def stage = new SimpleStage(it.name, it)
      applicationContext.autowireCapableBeanFactory.autowireBean(stage)
      // TODO: this should be a prototype scoped bean or use a factory I guess
      stages[it.name] = stage
    }
  }

  private Pipeline parseConfig(String configJson) {
    // TODO: map directly to the Pipeline class
    List<Map<String, ? extends Serializable>> configMap = mapper.readValue(configJson, new TypeReference<List<Map>>() {
    }) as List
    // TODO: this needs to change as we can't locate the pipeline using this id
    new Pipeline("meh-i-dont-know", configMap.collect {
      def stage = new Stage(it.remove("type").toString())
      stage.context.putAll(it)
      return stage
    })
  }

  private Job createJobFrom(Pipeline pipeline) {
    // TODO: can we get any kind of meaningful identifier from the mayo config?
    def jobBuilder = jobs.get(pipeline.id)
                         .flow(PipelineInitializerTasklet.initializationStep(steps, pipeline))
    def stageBuilders = stageBuildersFor(pipeline)
    def flow = (JobFlowBuilder) stageBuilders.inject(jobBuilder, this.&createStage)
    flow.build().build()
  }

  private List<StageBuilder> stageBuildersFor(Pipeline pipeline) {
    pipeline.stages.collect {
      String beanName = it.name
      if (it.context.providerType) {
        beanName = "${it.name}_$it.context.providerType"
      }

      if (stages.containsKey(beanName)) {
        stages.get(beanName)
      } else {
        throw new NoSuchStageException(beanName)
      }
    }
  }

  private JobFlowBuilder createStage(JobFlowBuilder jobBuilder, StageBuilder stageBuilder) {
    stageBuilder.build(jobBuilder)
  }
}
