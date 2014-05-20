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

package com.netflix.bluespar.oort.remoting

class AggregateRemoteResource implements RemoteResource {

  final Map<String, RemoteResource> remoteResources

  AggregateRemoteResource(Map<String, RemoteResource> remoteResources) {
    this.remoteResources = remoteResources
  }

  @Override
  Map get(String uri) {
    def result = [:]
    remoteResources.values().each {
      result << it.get(uri)
    }
    result
  }

  @Override
  List query(String uri) {
    def results = []
    remoteResources.values().each {
      results.addAll it.query(uri)
    }
    results
  }

  RemoteResource getRemoteResource(String name) {
    remoteResources[name]
  }
}
