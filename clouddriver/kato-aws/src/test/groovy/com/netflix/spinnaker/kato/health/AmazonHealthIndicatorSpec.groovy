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


package com.netflix.spinnaker.kato.health

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeAccountAttributesResult
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import org.springframework.boot.actuate.endpoint.HealthEndpoint
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter
import org.springframework.boot.actuate.health.OrderedHealthAggregator
import org.springframework.http.HttpStatus
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import spock.lang.Ignore
import spock.lang.Specification

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

@Ignore
class AmazonHealthIndicatorSpec extends Specification {

  def "health fails when no aws credentials are available"() {
    setup:
    def holder = Mock(AccountCredentialsProvider)
    def credz = [new NetflixAmazonCredentials(name: "foo")]
    holder.getAll() >> creds
    holder.getCredentials("foo") >> credz[0]
    def endpoint = new HealthEndpoint(new OrderedHealthAggregator(), [health: new AmazonHealthIndicator(accountCredentialsProvider: holder)])
    def mvc = standaloneSetup(endpoint).setMessageConverters new MappingJackson2HttpMessageConverter() build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/health")).andReturn()

    then:
    result.response.status == HttpStatus.INTERNAL_SERVER_ERROR.value()
  }

  def "health fails when amazon appears unreachable"() {
    setup:
    def holder = Mock(AccountCredentialsProvider)
    def credz = [new NetflixAmazonCredentials(name: "foo")]
    holder.getAll() >> creds
    holder.getCredentials("foo") >> credz[0]
    def mockEc2 = Mock(AmazonEC2)
    mockEc2.describeAccountAttributes() >> { throw new AmazonServiceException("fail") }
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAmazonEC2(_, _) >> mockEc2
    def endpoint = new EndpointMvcAdapter(new HealthEndpoint(new AmazonHealthIndicator(accountCredentialsProvider: holder, amazonClientProvider: mockAmazonClientProvider)))
    def mvc = standaloneSetup(endpoint).setMessageConverters new MappingJackson2HttpMessageConverter() build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/health")).andReturn()

    then:
    result.response.status == HttpStatus.SERVICE_UNAVAILABLE.value()
  }

  def "health succeeds when amazon is reachable"() {
    setup:
    def holder = Mock(AccountCredentialsProvider)
    def credz = [new NetflixAmazonCredentials(name: "foo")]
    holder.getAll() >> creds
    holder.getCredentials("foo") >> credz[0]
    def mockEc2 = Mock(AmazonEC2)
    mockEc2.describeAccountAttributes() >> { Mock(DescribeAccountAttributesResult) }
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAmazonEC2(_, _) >> mockEc2
    def endpoint = new EndpointMvcAdapter(new HealthEndpoint(new AmazonHealthIndicator(namedAccountCredentialsHolder: holder, amazonClientProvider: mockAmazonClientProvider)))
    def mvc = standaloneSetup(endpoint).setMessageConverters new MappingJackson2HttpMessageConverter() build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/health")).andReturn()

    then:
    result.response.status == HttpStatus.OK.value()
  }
}
