package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class NotifyEchoSpec extends Specification {

  @Subject
  NotifyEchoTask task = new NotifyEchoTask()

  void "should send notification"() {
    setup:
    def pipeline = new Pipeline()
    task.echo = Mock(EchoService)

    def stage = new PipelineStage(pipeline, "whatever", [
      application        : "myapp",
      "notification.type": "testtype",
      "randomAttr"       : 'random'
    ]).asImmutable()

    when:
    task.execute(stage)

    then:
    1 * task.echo.recordEvent(
      {
        it.details.type == 'testtype' &&
          it.details.application == 'myapp' &&
          it.details.source == 'kato' &&
          it.content.randomAttr == 'random'
      }
    )
  }

  void 'does not send an event if echo is not configured'() {
    setup:
    task.echo = null

    def stage = new PipelineStage(type: "whatever").asImmutable()

    when:
    task.execute(stage)

    then:
    0 * task.echo._

  }

}
