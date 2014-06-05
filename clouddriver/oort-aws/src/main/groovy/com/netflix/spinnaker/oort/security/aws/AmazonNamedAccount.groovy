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

package com.netflix.spinnaker.oort.security.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.oort.security.NamedAccount
import groovy.transform.CompileStatic

@CompileStatic
class AmazonNamedAccount implements NamedAccount<AmazonCredentials> {
  String name
  String edda
  String front50
  String discovery
  List<String> regions
  Class<AmazonCredentials> type = AmazonCredentials

  private AWSCredentialsProvider provider

  /**
   * @deprecated FOR TESTING ONLY
   */
  @Deprecated
  AmazonNamedAccount() {

  }

  AmazonNamedAccount(AWSCredentialsProvider provider, String name, String edda, String front50, String discovery, List<String> regions) {
    this.provider = provider
    this.name = name
    this.edda = edda
    this.front50 = front50
    this.discovery = discovery
    this.regions = regions
  }

  @Override
  AmazonCredentials getCredentials() {
    new AmazonCredentials(provider.credentials, name, edda)
  }

  @Override
  Class<AmazonCredentials> getType() {
    AmazonCredentials
  }
}
