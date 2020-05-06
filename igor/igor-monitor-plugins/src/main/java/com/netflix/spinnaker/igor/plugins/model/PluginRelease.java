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
 *
 */
package com.netflix.spinnaker.igor.plugins.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Optional;

public class PluginRelease {
  private final String pluginId;
  private final String version;
  private final String releaseDate;
  private final List<ServiceRequirement> requires;
  private final String binaryUrl;
  private final boolean preferred;
  private final String lastModified;

  public PluginRelease(
      String pluginId,
      String version,
      String releaseDate,
      List<ServiceRequirement> requires,
      String binaryUrl,
      boolean preferred,
      String lastModified) {
    this.pluginId = pluginId;
    this.version = version;
    this.releaseDate = releaseDate;
    this.requires = requires;
    this.binaryUrl = binaryUrl;
    this.preferred = preferred;
    this.lastModified = lastModified;
  }

  public String getPluginId() {
    return pluginId;
  }

  public String getVersion() {
    return version;
  }

  public String getReleaseDate() {
    return releaseDate;
  }

  public List<ServiceRequirement> getRequires() {
    return requires;
  }

  public String getBinaryUrl() {
    return binaryUrl;
  }

  public boolean isPreferred() {
    return preferred;
  }

  public String getLastModified() {
    return lastModified;
  }

  @JsonIgnore
  public String getTimestamp() {
    return Optional.ofNullable(lastModified).orElse(releaseDate);
  }

  public static class ServiceRequirement {
    private final String service;
    private final String operator;
    private final String version;

    public ServiceRequirement(String service, String operator, String version) {
      this.service = service;
      this.operator = operator;
      this.version = version;
    }

    public String getService() {
      return service;
    }

    public String getOperator() {
      return operator;
    }

    public String getVersion() {
      return version;
    }
  }
}
