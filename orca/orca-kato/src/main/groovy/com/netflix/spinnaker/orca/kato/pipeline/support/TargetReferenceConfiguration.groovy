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

package com.netflix.spinnaker.orca.kato.pipeline.support

class TargetReferenceConfiguration {
  enum Target {
    current_asg, ancestor_asg, current_asg_dynamic, ancestor_asg_dynamic, oldest_asg
  }

  Target target
  String asgName
  String cluster
  String credentials
  List<String> regions
  String providerType = "aws"
}
