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

package com.netflix.bluespar.kato.deploy.aws

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Tag
import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.data.task.TaskRepository
import com.netflix.bluespar.kato.deploy.aws.AutoScalingWorker
import spock.lang.Specification

class AutoScalingWorkerUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "deploy workflow is create security group, create launch config, create asg"() {
    setup:
    def asgName = "myasg-v000"
    def launchConfigName = "launchConfig"
    def mockAutoScalingWorker = Spy(AutoScalingWorker)

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getSecurityGroupForApplication() >> "sg-1234"
    1 * mockAutoScalingWorker.getAncestorAsg() >> null
    1 * mockAutoScalingWorker.getAutoScalingGroupName(0) >> asgName
    1 * mockAutoScalingWorker.getUserData(asgName, launchConfigName) >> { "" }
    1 * mockAutoScalingWorker.getLaunchConfigurationName(0) >> launchConfigName
    1 * mockAutoScalingWorker.createLaunchConfiguration(_, _, _) >> { launchConfigName }
    1 * mockAutoScalingWorker.createAutoScalingGroup(_, _) >> {}
  }

  void "deploy favors security groups of ancestor asg"() {
    setup:
    def mockAutoScalingWorker = Spy(AutoScalingWorker)

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getUserData(_, _) >> null
    1 * mockAutoScalingWorker.getLaunchConfigurationName(_) >> "launchConfigName"
    1 * mockAutoScalingWorker.getSecurityGroupForApplication() >> "sg-1234"
    1 * mockAutoScalingWorker.getAncestorAsg() >> {
      [autoScalingGroupName: "asgard-test-v000", launchConfigurationName: "asgard-test-v000-launchConfigName"]
    }
    1 * mockAutoScalingWorker.getSecurityGroupsForLaunchConfiguration("asgard-test-v000-launchConfigName") >> {
      ['sg-5678']
    }
    1 * mockAutoScalingWorker.createLaunchConfiguration("launchConfigName", null, _) >> {
      'launchConfigName'
    }
    1 * mockAutoScalingWorker.createAutoScalingGroup(_, _) >> {}
  }

  void "security group is created for app if one is not found"() {
    setup:
    def mockAutoScalingWorker = Spy(AutoScalingWorker)

    when:
    mockAutoScalingWorker.deploy()

    then:
    1 * mockAutoScalingWorker.getUserData(_, _) >> null
    1 * mockAutoScalingWorker.getLaunchConfigurationName(_) >> "launchConfigName"
    1 * mockAutoScalingWorker.getSecurityGroupForApplication() >> null
    1 * mockAutoScalingWorker.createSecurityGroup() >> { "sg-1234" }
    1 * mockAutoScalingWorker.getAncestorAsg() >> null
    1 * mockAutoScalingWorker.createLaunchConfiguration("launchConfigName", null, _) >> { "launchConfigName" }
    1 * mockAutoScalingWorker.createAutoScalingGroup(_, _) >> {}
  }

  void "subnet ids are retrieved when type is specified"() {
    setup:
    def ec2 = Mock(AmazonEC2)
    ec2.describeSubnets() >> {
      def mock = Mock(DescribeSubnetsResult)
      mock.getSubnets() >> [new Subnet()
                              .withSubnetId("123")
                              .withState("available")
                              .withAvailabilityZone("us-west-1a")
                              .withTags([new Tag("immutable_metadata", '{ "purpose": "internal", "target": "ec2"}')])
      ]
      mock
    }
    def worker = new AutoScalingWorker(amazonEC2: ec2, subnetType: AutoScalingWorker.SubnetType.INTERNAL,
      availabilityZones: ["us-west-1a"])

    when:
    def results = worker.subnetIds

    then:
    results.first() == "123"
  }

}
