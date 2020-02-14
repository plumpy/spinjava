/*
 * Copyright 2020 Amazon.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.igor.model;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Map;
import lombok.Data;

@Data
public class AwsCodeBuildStageDefinition implements RetryableStageDefinition {
  private String account;
  private String projectName;
  private boolean sourceOverride;
  private AwsCodeBuildSourceArtifact source;
  private String sourceVersion;
  private Map<String, String> environmentVariables;
  private String image;
  private String buildspec;
  private AwsCodeBuildExecution buildInfo;
  private int consecutiveErrors;

  @Data
  public static class AwsCodeBuildSourceArtifact {
    private String sourceType;
    private String artifactId;
    private Artifact artifact;
  }
}
