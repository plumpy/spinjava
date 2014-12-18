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
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertSecurityGroupDescription.SecurityGroupIngress
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertSecurityGroupAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new UpsertSecurityGroupDescription().with {
    name = "foo"
    description = "desc"
    securityGroupIngress = [
      new SecurityGroupIngress().with {
        name = "bar"
        startPort = 111
        endPort = 112
        type = UpsertSecurityGroupDescription.IngressType.tcp
        it
      }
    ]
    it
  }

  @Subject op = new UpsertSecurityGroupAtomicOperation(description)

  @Shared
  AmazonEC2 ec2

  def setup() {
    ec2 = Mock(AmazonEC2)
    def clientProvider = Mock(AmazonClientProvider)
    clientProvider.getAmazonEC2(_, _) >> ec2
    op.amazonClientProvider = clientProvider
  }

  void "non-existent security groups should be created"() {
    when:
    op.operate([])

    then:
    1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(securityGroups: [new SecurityGroup(groupName: "bar", groupId: "456")])

    then:
    1 * ec2.createSecurityGroup(new CreateSecurityGroupRequest(groupName: "foo", description: "desc")) >> new CreateSecurityGroupResult(groupId: "123")
    1 * ec2.describeSecurityGroups(new DescribeSecurityGroupsRequest(groupIds: ["123"])) >> new DescribeSecurityGroupsResult(securityGroups: [new SecurityGroup(groupName: "foo", groupId: "123")])

    1 * ec2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(groupId: "123",
            ipPermissions: [
                    new IpPermission(ipProtocol: "tcp", fromPort: 111, toPort: 112, userIdGroupPairs: [
                            new UserIdGroupPair(groupId: "456")
                    ])
            ])
    )
  }

  void "existing permissions are revoked before new ones are applied"() {
    when:
    op.operate([])

    then:
    1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
            securityGroups: [
                    new SecurityGroup(groupName: "foo", groupId: "123", ipPermissions: [
                            new IpPermission(fromPort: 80, toPort: 81, userIdGroupPairs: [new UserIdGroupPair(groupId: "grp")])
                    ]),
                    new SecurityGroup(groupName: "bar", groupId: "456")
            ]
    )
    1 * ec2.revokeSecurityGroupIngress(_) >> { RevokeSecurityGroupIngressRequest request ->
      assert request.ipPermissions[0].userIdGroupPairs[0].groupId == "grp"
      assert request.ipPermissions[0].fromPort == 80
      assert request.ipPermissions[0].toPort == 81
    }
    1 * ec2.authorizeSecurityGroupIngress(_) >> { AuthorizeSecurityGroupIngressRequest request ->
      assert request.ipPermissions[0].userIdGroupPairs[0].groupId == "456"
      assert request.ipPermissions[0].fromPort == 111
      assert request.ipPermissions[0].toPort == 112
    }
  }
}
