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


package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository

class DeregisterInstancesFromLoadBalancerAtomicOperationUnitSpec extends InstanceLoadBalancerRegistrationUnitSpecSupport {
  def setupSpec() {
    description.credentials = TestCredential.named('test')
    description.instanceIds = ["i-123456"]
    op = new DeregisterInstancesFromLoadBalancerAtomicOperation(description)
  }

  void 'should deregister instances from load balancers'() {
    setup:
    TaskRepository.threadLocalTask.set(Mock(Task) {
      _ * updateStatus(_,_)
    })

    def asg = Mock(AutoScalingGroup) {
      1 * getLoadBalancerNames() >> ["lb1"]
      1 * getInstances() >> [new Instance().withInstanceId("i-123456")]
      0 * _._
    }

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroups([description.asgName]) >> [asg]
    1 * loadBalancing.deregisterInstancesFromLoadBalancer(_) >> { DeregisterInstancesFromLoadBalancerRequest req ->
      assert req.instances*.instanceId == description.instanceIds
      assert req.loadBalancerName == "lb1"
    }
  }

  void 'should noop task if no load balancers found'() {
    setup:
    TaskRepository.threadLocalTask.set(Mock(Task) {
      _ * updateStatus(_,_)
      0 * fail()
    })

    def asg = Mock(AutoScalingGroup) {
      1 * getAutoScalingGroupName() >> "asg-123456"
      1 * getLoadBalancerNames() >> []
      1 * getInstances() >> description.instanceIds.collect { new Instance().withInstanceId(it) }
      0 * _._
    }

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroups([description.asgName]) >> [asg]
    0 * loadBalancing.deregisterInstancesFromLoadBalancer(_)
  }
}
