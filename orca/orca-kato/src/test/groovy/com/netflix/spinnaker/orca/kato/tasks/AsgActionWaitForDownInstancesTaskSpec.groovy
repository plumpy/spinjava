package com.netflix.spinnaker.orca.kato.tasks

import spock.lang.Specification
import spock.lang.Subject
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import retrofit.client.Response
import retrofit.mime.TypedInput

/**
 * Created by aglover on 7/10/14.
 */
class AsgActionWaitForDownInstancesTaskSpec extends Specification {
  @Subject task = new AsgActionWaitForDownInstancesTask()

  def mapper = new ObjectMapper()

  void "should check cluster to get server groups"() {
    given:
    task.objectMapper = mapper
    def response = GroovyMock(Response)
    response.getStatus() >> 200
    response.getBody() >> {
      def input = Mock(TypedInput)
      input.in() >> {
        def jsonObj = [
            [
                name        : "front50",
                serverGroups: [
                    [
                        region   : "us-west-1",
                        name     : "front50-v000",
                        asg      : [
                            minSize: 1
                        ],
                        instances: [
                            [
                                isHealthy: false
                            ]
                        ]
                    ]
                ]
            ]
        ]
        new ByteArrayInputStream(mapper.writeValueAsString(jsonObj).bytes)
      }
      input
    }
    task.oortService = Stub(OortService) {
      getCluster(*_) >> response
    }

    and:
    def context = new SimpleTaskContext()
    context."targetop.asg.enableAsg.name" = "front50"
    context."targetop.asg.enableAsg.regions" = ['us-west-1']
    context."deploy.account.name" = "test"
//    context."deploy.server.groups" = ["us-west-1": ["front50-v000"]]

    expect:
    task.execute(context).status == TaskResult.Status.SUCCEEDED

  }
}
