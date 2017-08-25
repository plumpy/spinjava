/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KubernetesManifestOperationDescription implements DeployDescription, CredentialsNameable, ApplicationNameable {
  String application;
  String account;
  List<KubernetesManifest> manifest = new ArrayList<>();
  KubernetesNamedAccountCredentials credentials;
}
