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
package com.netflix.spinnaker.kato.services

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.ResumeProcessesRequest
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest
import com.netflix.spinnaker.kato.model.aws.AutoScalingProcessType
import spock.lang.Specification
import spock.lang.Subject

class AsgServiceSpec extends Specification {

  def mockThrottleService = Mock(ThrottleService)
  def mockAmazonAutoScaling = Mock(AmazonAutoScaling)
  @Subject def asgService = new AsgService( mockThrottleService, mockAmazonAutoScaling)

  void 'should get auto scaling groups'() {
    when:
    def result = asgService.getAutoScalingGroups(["asg1", "asg2"])

    then:
    result == ["asg1", "asg2"].collect { new AutoScalingGroup(autoScalingGroupName: it)}

    and:
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: ["asg1", "asg2"])) >>
      new DescribeAutoScalingGroupsResult(autoScalingGroups: ["asg1", "asg2"].collect { new AutoScalingGroup(autoScalingGroupName: it)})
    0 * _
  }

  void 'should get single auto scaling group'() {
    when:
    def result = asgService.getAutoScalingGroup("asg1")

    then:
    result == new AutoScalingGroup(autoScalingGroupName: "asg1")

    and:
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: ["asg1"])) >>
      new DescribeAutoScalingGroupsResult(autoScalingGroups: [new AutoScalingGroup(autoScalingGroupName: "asg1")])
    0 * _
  }

  void 'should return null when auto scaling group does not exist'() {
    when:
    def result = asgService.getAutoScalingGroup("asg1")

    then:
    result == null

    and:
    1 * mockAmazonAutoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: ["asg1"])) >>
      new DescribeAutoScalingGroupsResult(autoScalingGroups: [])
    0 * _
  }

  void 'should suspend processes'() {
    when:
    asgService.suspendProcesses("asg1", AutoScalingProcessType.with { [Launch, Terminate] })

    then:
    1 * mockAmazonAutoScaling.suspendProcesses(new SuspendProcessesRequest(autoScalingGroupName: "asg1", scalingProcesses: ["Launch", "Terminate"]))
    0 * _
  }

  void 'should resume processes'() {
    when:
    asgService.resumeProcesses("asg1", AutoScalingProcessType.with { [Launch, Terminate] })

    then:
    1 * mockAmazonAutoScaling.resumeProcesses(new ResumeProcessesRequest(autoScalingGroupName: "asg1", scalingProcesses: ["Launch", "Terminate"]))
    0 * _
  }
}
