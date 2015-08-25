package com.netflix.spinnaker.echo.pipelinetriggers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.services.Front50Service
import com.netflix.spinnaker.echo.test.RetrofitStubs
import rx.functions.Action1
import rx.schedulers.Schedulers
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.echo.model.BuildEvent.Result.*
import static java.util.concurrent.TimeUnit.SECONDS
import static rx.Observable.empty
import static rx.Observable.just

class BuildEventMonitorSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
  def pipelineCache = Mock(PipelineCache)
  def subscriber = Mock(Action1)
  def registry = new ExtendedRegistry(new NoopRegistry())

  @Subject
  def monitor = new BuildEventMonitor(pipelineCache, subscriber, registry)

  def "triggers pipelines for successful builds"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.application == pipeline.application && it.name == pipeline.name
    })

    where:
    event = createBuildEventWith(SUCCESS)
    pipeline = createPipelineWith(enabledJenkinsTrigger)
  }

  def "attaches the trigger to the pipeline"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.trigger.type == enabledJenkinsTrigger.type
      it.trigger.master == enabledJenkinsTrigger.master
      it.trigger.job == enabledJenkinsTrigger.job
      it.trigger.buildNumber == event.content.project.lastBuild.number
    })

    where:
    event = createBuildEventWith(SUCCESS)
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger)
  }

  def "an event can trigger multiple pipelines"() {
    given:
    pipelineCache.getPipelines() >> pipelines

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    pipelines.size() * subscriber.call(_ as Pipeline)

    where:
    event = createBuildEventWith(SUCCESS)
    pipelines = (1..2).collect {
      Pipeline.builder()
              .application("application")
              .name("pipeline$it")
              .id("id")
              .triggers([enabledJenkinsTrigger])
              .build()
    }
  }

  @Unroll
  def "does not trigger pipelines for #description builds"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

    where:
    result   | _
    BUILDING | _
    FAILURE  | _
    ABORTED  | _
    null     | _

    pipeline = createPipelineWith(enabledJenkinsTrigger)
    event = createBuildEventWith(result)
    description = result ?: "unbuilt"
  }

  @Unroll
  def "does not trigger #description pipelines"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

    where:
    trigger                                 | description
    disabledJenkinsTrigger                  | "disabled"
    nonJenkinsTrigger                       | "non-Jenkins"
    enabledJenkinsTrigger.withMaster("FOO") | "different master"
    enabledJenkinsTrigger.withJob("FOO")    | "different job"

    pipeline = createPipelineWith(trigger)
    event = createBuildEventWith(SUCCESS)
  }

  @Unroll
  def "does not trigger a pipeline that has an enabled trigger with missing #field"() {
    given:
    pipelineCache.getPipelines() >> [badPipeline, goodPipeline]
    println objectMapper.writeValueAsString(createBuildEventWith(SUCCESS))

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({ it.id == goodPipeline.id })

    where:
    trigger                                | field
    enabledJenkinsTrigger.withMaster(null) | "master"
    enabledJenkinsTrigger.withJob(null)    | "job"

    event = createBuildEventWith(SUCCESS)
    goodPipeline = createPipelineWith(enabledJenkinsTrigger)
    badPipeline = createPipelineWith(trigger)
  }
}
