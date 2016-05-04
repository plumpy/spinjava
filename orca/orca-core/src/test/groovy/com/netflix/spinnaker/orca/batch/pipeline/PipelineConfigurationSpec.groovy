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

package com.netflix.spinnaker.orca.batch.pipeline

import com.google.common.collect.Maps
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.NoSuchStageException
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.StageDetailsTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.hamcrest.ContainsAllOf.containsAllOf
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD
import static spock.util.matcher.HamcrestSupport.expect

@ContextConfiguration(classes = [BatchTestConfiguration, TestConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PipelineConfigurationSpec extends Specification {

  @Shared @AutoCleanup("destroy") EmbeddedRedis embeddedRedis

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
  }

  Pool<Jedis> jedisPool = embeddedRedis.pool

  @Autowired AbstractApplicationContext applicationContext
  @Autowired JobBuilderFactory jobs
  @Autowired StepBuilderFactory steps
  @Autowired JobLauncher jobLauncher
  @Autowired JobRepository jobRepository

  @Subject pipelineStarter = new PipelineStarter()
  @Subject pipelineJobBuilder = new PipelineJobBuilder()

  def fooTask = Mock(Task)
  def barTask = Mock(Task)
  def bazTask = Mock(Task)

  @Shared mapper = new OrcaObjectMapper()
  def executionRepository = new JedisExecutionRepository(new NoopRegistry(), jedisPool, 1, 50)

  def setup() {

    def fooStage = new TestStage("foo", steps, executionRepository, fooTask)
    def barStage = new TestStage("bar", steps, executionRepository, barTask)
    def bazStage = new TestStage("baz", steps, executionRepository, bazTask)

    applicationContext.beanFactory.with {
      registerSingleton "mapper", mapper
      registerSingleton "pipelineJobBuilder", pipelineJobBuilder
      registerSingleton "executionRepository", executionRepository
      registerSingleton "stageDetails", new StageDetailsTask()
      registerSingleton "fooStage", fooStage
      registerSingleton "barStage", barStage
      registerSingleton "bazStage", bazStage

      autowireBean pipelineJobBuilder
      autowireBean pipelineStarter
    }

    fooStage.applicationContext = applicationContext
    barStage.applicationContext = applicationContext
    bazStage.applicationContext = applicationContext

    // need to do this so that our stages are picked up
    pipelineJobBuilder.initialize()
  }

  def "an unknown stage type results in an exception"() {
    when:
    pipelineStarter.start configJson

    then:
    thrown NoSuchStageException

    where:
    config = [application: "app", name: "my-pipeline", stages: [[type: "qux"]]]
    configJson = mapper.writeValueAsString(config)
  }

  def "a single step is constructed from mayo's json config"() {
    when:
    pipelineStarter.start configJson

    then:
    1 * fooTask.execute(_) >> DefaultTaskResult.SUCCEEDED

    where:
    config = [application: "app", name: "my-pipeline", stages: [[type: "foo"]]]
    configJson = mapper.writeValueAsString(config)
  }

  def "multiple steps are constructed from mayo's json config"() {
    when:
    pipelineStarter.start configJson

    then:
    1 * fooTask.execute(_) >> DefaultTaskResult.SUCCEEDED

    then:
    1 * barTask.execute(_) >> DefaultTaskResult.SUCCEEDED

    then:
    1 * bazTask.execute(_) >> DefaultTaskResult.SUCCEEDED

    where:
    config = [
      application: "app",
      name       : "my-pipeline",
      stages     : [
        [type: "foo"],
        [type: "bar"],
        [type: "baz"]
      ]
    ]
    configJson = mapper.writeValueAsString(config)
  }

  def "config is serialized to stage context"() {
    given:
    Map context
    1 * fooTask.execute(_) >> { Stage stage ->
      context = stage.context
      DefaultTaskResult.SUCCEEDED
    }

    when:
    pipelineStarter.start configJson

    then:
    expect context, containsAllOf(expectedInputs)

    where:
    config = [
      application: "app",
      name       : "my-pipeline",
      stages     : [[type: "foo", region: "us-west-1", os: "ubuntu"]]
    ]
    configJson = mapper.writeValueAsString(config)
    expectedInputs = Maps.filterKeys(config.stages.first()) { it != "type" }
  }

  def "pipeline is persisted"() {
    given:
    def mockExecutionRepo = Mock(ExecutionRepository)
    pipelineStarter.@executionRepository = mockExecutionRepo

    when:
    pipelineStarter.start(configJson)

    then:
    2 * mockExecutionRepo.store(_ as Pipeline)

    where:
    config = [
      application: "app",
      name       : "my-pipeline",
      stages     : [
        [type: "foo"],
        [type: "bar"],
        [type: "baz"]
      ]
    ]
    configJson = mapper.writeValueAsString(config)
  }
}
