package com.netflix.spinnaker.orca.initialization

import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

//@Component
//@Scope(SCOPE_PROTOTYPE)
@Slf4j
@CompileStatic
class PipelineRestartHandler extends AbstractNotificationHandler {

  PipelineRestartHandler(Map input) {
    super(input)
  }

  @Override
  String getHandlerType() {
    PipelineRestartAgent.NOTIFICATION_TYPE
  }

  @Override
  void handle(Map pipeline) {
    try {
      pipelineStarter.resume(pipelineStarter.executionRepository.retrievePipeline(pipeline.id as String))
    } catch (e) {
      log.error("Unable to resume pipeline", e)
      throw e
    }
  }
}
