/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.gitlabci;

import com.netflix.spinnaker.igor.build.model.GenericProject;
import com.netflix.spinnaker.igor.history.model.BuildContent;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GitlabCiBuildContent implements BuildContent {
  private static final String TYPE = "gitlab-ci";
  private String master;
  private GenericProject project;

  @Override
  public String getType() {
    return TYPE;
  }
}
