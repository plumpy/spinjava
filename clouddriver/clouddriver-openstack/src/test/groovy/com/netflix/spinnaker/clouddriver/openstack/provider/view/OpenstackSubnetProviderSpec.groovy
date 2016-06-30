/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSubnet
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS

class OpenstackSubnetProviderSpec extends Specification {

  String account = 'test'
  String region = 'west'

  OpenstackSubnetProvider provider
  Cache cache
  ObjectMapper objectMapper

  void "setup"() {
    objectMapper = Mock(ObjectMapper)
    cache = Mock(Cache)
    provider = new OpenstackSubnetProvider(cache, objectMapper)
  }

  void "test get all"() {
    given:
    Map<String, String> attributes = [:]
    CacheData mockData = Mock(CacheData)
    Collection<CacheData> cacheData = [mockData]
    Collection<String> filters = Mock(Collection)
    OpenstackSubnet subnet = Mock(OpenstackSubnet)

    when:
    Set<OpenstackSubnet> result = provider.getAll()

    then:
    1 * cache.filterIdentifiers(SUBNETS.ns, "${OpenstackCloudProvider.ID}:${SUBNETS.ns}:*:*:*") >> filters
    1 * cache.getAll(SUBNETS.ns, filters, _) >> cacheData
    1 * mockData.attributes >> attributes
    1 * objectMapper.convertValue(attributes, OpenstackSubnet) >> subnet
    result?.first() == subnet
    noExceptionThrown()
  }

  @Unroll
  void "test get all - #testCase"() {
    given:
    Collection<String> filters = Mock(Collection)

    when:
    Set<OpenstackSubnet> result = provider.getAll()

    then:
    1 * cache.filterIdentifiers(SUBNETS.ns, "${OpenstackCloudProvider.ID}:${SUBNETS.ns}:*:*:*") >> filters
    1 * cache.getAll(SUBNETS.ns, filters, _) >> queryResult

    and:
    result != null
    result.isEmpty()
    noExceptionThrown()

    where:
    testCase | queryResult
    'empty'  | []
    'null'   | null
  }

  void "test get all - exception"() {
    given:
    Collection<String> filters = Mock(Collection)
    Throwable throwable = new JedisException('test')

    when:
    provider.getAll()

    then:
    1 * cache.filterIdentifiers(SUBNETS.ns, "${OpenstackCloudProvider.ID}:${SUBNETS.ns}:*:*:*") >> filters
    1 * cache.getAll(SUBNETS.ns, filters, _) >> { throw throwable }
    JedisException exception = thrown(JedisException)
    exception == throwable
  }
}
