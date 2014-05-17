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

package com.netflix.bluespar.kato.security.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.bluespar.amazon.security.AmazonCredentials
import com.netflix.bluespar.kato.security.NamedAccountCredentials
import org.springframework.data.annotation.Transient

import javax.xml.bind.annotation.XmlTransient

class AmazonNamedAccountCredentials implements NamedAccountCredentials<AmazonCredentials> {
  @JsonIgnore
  @XmlTransient
  @Transient
  final AmazonCredentials credentials

  AmazonNamedAccountCredentials(AWSCredentialsProvider provider, String environment) {
    this.credentials = new AmazonCredentials(provider.credentials, environment)
  }
}
