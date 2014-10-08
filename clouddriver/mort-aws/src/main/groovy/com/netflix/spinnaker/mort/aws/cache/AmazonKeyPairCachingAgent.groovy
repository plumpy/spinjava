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





package com.netflix.spinnaker.mort.aws.cache

import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.CachingAgent
import groovy.transform.Immutable
import rx.Observable

@Immutable(knownImmutables = ["ec2", "cacheService"])
class AmazonKeyPairCachingAgent implements CachingAgent {
  final String account
  final String region
  final AmazonEC2 ec2
  final CacheService cacheService

  @Override
  void call() {
    println "[$account:$region:kpr] - Caching..."
    def result = ec2.describeKeyPairs()
    Observable.from(result.keyPairs).subscribe {
      cacheService.put(Keys.getKeyPairKey(it.keyName, region, account), it)
    }
  }
}
