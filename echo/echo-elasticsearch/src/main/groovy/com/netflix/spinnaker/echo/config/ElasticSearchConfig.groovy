/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.config

import static org.elasticsearch.node.NodeBuilder.nodeBuilder

import io.searchbox.client.JestClient
import io.searchbox.client.JestClientFactory
import io.searchbox.client.config.HttpClientConfig
import org.elasticsearch.client.Client
import org.elasticsearch.node.Node
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Elastic Search configuration for Embedded Jest Client
 */
@Configuration
class ElasticSearchConfig {

    Client client

    @Bean
    @ConditionalOnMissingClass(name = 'com.netflix.spinnaker.platform.netflix.jest.DiscoveryAwareJestClient')
    JestClient manufacture() {
        Node node = nodeBuilder().local(true).node()
        client = node.client()
        JestClientFactory factory = new JestClientFactory()
        factory.setHttpClientConfig(
            new HttpClientConfig.Builder('http://localhost:9200').multiThreaded(true).build())
        factory.object
    }

}
