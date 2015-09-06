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

import com.amazonaws.services.ec2.model.Vpc
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.aws.model.AmazonVpc
import com.netflix.spinnaker.mort.model.VpcProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.mort.aws.cache.Keys.Namespace.VPCS

@Component
class AmazonVpcProvider implements VpcProvider<AmazonVpc> {

  private static final String NAME_TAG_KEY = 'Name'
  private static final String DEPRECATED_TAG_KEY = 'is_deprecated'

  private final AmazonCloudProvider amazonCloudProvider
  private final Cache cacheView
  private final AmazonObjectMapper objectMapper

  @Autowired
  AmazonVpcProvider(AmazonCloudProvider amazonCloudProvider, Cache cacheView, AmazonObjectMapper amazonObjectMapper) {
    this.amazonCloudProvider = amazonCloudProvider
    this.cacheView = cacheView
    this.objectMapper = amazonObjectMapper
  }

  @Override
  Set<AmazonVpc> getAll() {
    cacheView.getAll(VPCS.ns, RelationshipCacheFilter.none()).collect(this.&fromCacheData)
  }

  AmazonVpc fromCacheData(CacheData cacheData) {
    def parts = Keys.parse(amazonCloudProvider, cacheData.id)
    def vpc = objectMapper.convertValue(cacheData.attributes, Vpc)
    def tag = vpc.tags.find { it.key == NAME_TAG_KEY }
    def isDeprecated = vpc.tags.find { it.key == DEPRECATED_TAG_KEY }?.value
    String name = tag?.value
    new AmazonVpc(id: vpc.vpcId,
      name: name,
      account: parts.account,
      region: parts.region,
      deprecated: new Boolean(isDeprecated)
    )
  }
}
