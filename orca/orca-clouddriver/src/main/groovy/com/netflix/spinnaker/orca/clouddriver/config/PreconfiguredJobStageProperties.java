/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.config;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data

public class PreconfiguredJobStageProperties {

  // Fields are public as job stages use reflection to access these directly from outside the class
  public boolean enabled = true;
  public String label;
  public String description;
  public String type;
  public List<PreconfiguredJobStageParameter> parameters;
  public boolean waitForCompletion = true;
  public String cloudProvider;
  public String credentials;
  public String region;
  public String propertyFile;
  public Map<String, Object> cluster = new HashMap<>();

}
