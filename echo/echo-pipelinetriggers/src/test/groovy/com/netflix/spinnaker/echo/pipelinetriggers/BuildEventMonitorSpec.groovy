package com.netflix.spinnaker.echo.pipelinetriggers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.test.RetrofitStubs
import rx.functions.Action1
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.echo.model.BuildEvent.Result.*

class BuildEventMonitorSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
  def pipelineCache = Mock(PipelineCache)
  def subscriber = Mock(Action1)
  def registry = Stub(Registry) {
    createId(*_) >> Stub(Id)
    counter(*_) >> Stub(Counter)
    gauge(*_) >> Integer.valueOf(1)
  }

  @Subject
  def monitor = new BuildEventMonitor(pipelineCache, subscriber, registry)

  @Unroll
  def "triggers pipelines for successful builds for #triggerType"() {
    given:
    def pipeline = createPipelineWith(trigger)
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.application == pipeline.application && it.name == pipeline.name
    })

    where:
    event                         | trigger               | triggerType
    createBuildEventWith(SUCCESS) | enabledJenkinsTrigger | 'jenkins'
    createStashEvent()            | enabledStashTrigger   | 'stash'
  }

  def "attaches jenkins trigger to the pipeline"() {
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

  def "attaches stash trigger to the pipeline"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({
      it.trigger.type == enabledStashTrigger.type
      it.trigger.project == enabledStashTrigger.project
      it.trigger.slug == enabledStashTrigger.slug
      it.trigger.hash == event.content.hash
    })

    where:
    event = createStashEvent()
    pipeline = createPipelineWith(enabledJenkinsTrigger, nonJenkinsTrigger, enabledStashTrigger, disabledStashTrigger)
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
    enabledStashTrigger                     | "stash trigger"
    disabledStashTrigger                    | "disabled stash trigger"
    nonJenkinsTrigger                       | "non-Jenkins"
    enabledJenkinsTrigger.withMaster("FOO") | "different master"
    enabledJenkinsTrigger.withJob("FOO")    | "different job"

    pipeline = createPipelineWith(trigger)
    event = createBuildEventWith(SUCCESS)
  }

  @Unroll
  def "does not trigger #description pipelinesfor stash"() {
    given:
    pipelineCache.getPipelines() >> [pipeline]

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    0 * subscriber._

    where:
    trigger                                       | description
    disabledJenkinsTrigger                        | "jenkins disabled"
    enabledJenkinsTrigger                         | "jenkins"
    disabledStashTrigger                          | "disabled stash trigger"
    enabledStashTrigger.withSlug("notSlug")       | "different slug"
    enabledStashTrigger.withSource("github")      | "different source"
    enabledStashTrigger.withProject("notProject") | "different project"

    pipeline = createPipelineWith(trigger)
    event = createStashEvent()
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

  @Unroll
  def "does not trigger a pipeline that has an enabled stash trigger with missing #field"() {
    given:
    pipelineCache.getPipelines() >> [badPipeline, goodPipeline]
    println objectMapper.writeValueAsString(createBuildEventWith(SUCCESS))

    when:
    monitor.processEvent(objectMapper.convertValue(event, Event))

    then:
    1 * subscriber.call({ it.id == goodPipeline.id })

    where:
    trigger                               | field
    enabledStashTrigger.withSlug(null)    | "slug"
    enabledStashTrigger.withProject(null) | "project"
    enabledStashTrigger.withSource(null)  | "source"

    event = createStashEvent()
    goodPipeline = createPipelineWith(enabledStashTrigger)
    badPipeline = createPipelineWith(trigger)
  }
}
