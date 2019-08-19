/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.kork.plugins.spring.configs;

import com.netflix.spinnaker.kork.plugins.spring.MalformedPluginConfigurationException;
import java.util.List;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PluginConfiguration {

  static final Pattern PLUGIN_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9]+\\/[\\w-]+");

  public String name;
  List<String> jars;
  public boolean enabled;

  public void validate() {
    if (!PLUGIN_NAME_PATTERN.matcher(name).matches()) {
      throw new MalformedPluginConfigurationException(
          String.format("Invalid plugin name: %s", name));
    }
  }

  @Override
  public String toString() {
    return new StringBuilder()
        .append("Plugin: " + name + ", ")
        .append("Jars: " + String.join(", ", jars))
        .toString();
  }
}
