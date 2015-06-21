/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.mort.aws.provider.view

import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Tag
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.aws.model.AmazonSubnet
import com.netflix.spinnaker.mort.aws.provider.AwsInfrastructureProvider
import spock.lang.Specification
import spock.lang.Subject

class AmazonSubnetProviderSpec extends Specification {

  Cache cache = Mock(Cache)
  ObjectMapper mapper = new AmazonObjectMapper()

  @Subject
  AmazonSubnetProvider provider = new AmazonSubnetProvider(cache, mapper)

  void "should retrieve all subnets"() {
    when:
    def result = provider.getAll()

    then:
    result == [
      new AmazonSubnet(
        id: 'subnet-00000001',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '10',
        availableIpAddressCount: 1,
        account: 'test',
        region: 'us-east-1',
        availabilityZone: 'us-east-1a',
        purpose: 'internal',
        target: 'EC2',
      ),
      new AmazonSubnet(
        id: 'subnet-00000002',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '11',
        availableIpAddressCount: 2,
        account: 'prod',
        region: 'us-west-1',
        availabilityZone: 'us-west-1a',
        purpose: 'external',
        target: 'EC2',
      )
    ] as Set

    and:
    1 * cache.getAll(Keys.Namespace.SUBNETS.ns, _ as CacheFilter) >> [
      snData('test', 'us-east-1',
        new Subnet(
          subnetId: 'subnet-00000001',
          state: 'available',
          vpcId: 'vpc-1',
          cidrBlock: '10',
          availableIpAddressCount: 1,
          availabilityZone: 'us-east-1a',
          tags: [new Tag(key: 'immutable_metadata', value: '{"purpose": "internal", "target": "EC2"}')]
        )),
      snData('prod', 'us-west-1', new Subnet(
        subnetId: 'subnet-00000002',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '11',
        availableIpAddressCount: 2,
        availabilityZone: 'us-west-1a',
        tags: [new Tag(key: 'immutable_metadata', value: '{"purpose": "external", "target": "EC2"}')]
      ))]
  }

  void "should parse purpose out of name tag"() {
    when:
    def result = provider.getAll()

    then:
    result == [
      new AmazonSubnet(
        id: 'subnet-00000001',
        state: 'available',
        vpcId: 'vpc-1',
        cidrBlock: '10',
        availableIpAddressCount: 1,
        account: 'test',
        region: 'us-east-1',
        availabilityZone: 'us-east-1a',
        purpose: 'external (vpc0)',
        target: 'EC2',
      )
    ] as Set

    and:
    1 * cache.getAll(Keys.Namespace.SUBNETS.ns, _ as CacheFilter) >> [snData('test', 'us-east-1', new Subnet(
      subnetId: 'subnet-00000001',
      state: 'available',
      vpcId: 'vpc-1',
      cidrBlock: '10',
      availableIpAddressCount: 1,
      availabilityZone: 'us-east-1a',
      tags: [
        new Tag(key: 'name', value: 'vpc0.external.us-east-1d'),
        new Tag(key: 'immutable_metadata', value: '{"target": "EC2"}')
      ]
    ))]
  }

  CacheData snData(String account, String region, Subnet subnet) {
    Map<String, Object> attributes = mapper.convertValue(subnet, AwsInfrastructureProvider.ATTRIBUTES)
    new DefaultCacheData(Keys.getSubnetKey(subnet.subnetId, region, account),
      attributes,
      [:]
    )
  }
}
