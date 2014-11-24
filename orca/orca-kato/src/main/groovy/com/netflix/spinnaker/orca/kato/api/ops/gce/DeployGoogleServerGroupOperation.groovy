/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.api.ops.gce

import com.netflix.spinnaker.orca.kato.api.Operation
import groovy.transform.CompileStatic

@CompileStatic
class DeployGoogleServerGroupOperation extends Operation {
  String application
  String stack
  String freeFormDetails
  int initialNumReplicas
  String image
  String instanceType
  String zone
  Source source = new Source()
  String credentials

  static class Source {
    String zone
    String serverGroupName
  }
}



