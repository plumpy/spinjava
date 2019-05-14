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

package com.netflix.spinnaker.clouddriver.model.securitygroups;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.SortedSet;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

/**
 * An abstract interface representing a security rule.
 *
 * @see IpRangeRule
 * @see SecurityGroupRule
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "class")
public interface Rule {
  /**
   * The port ranges associated with this rule
   *
   * @return
   */
  SortedSet<PortRange> getPortRanges();

  String getProtocol();

  @Data
  class PortRange implements Comparable<PortRange> {
    protected Integer startPort;
    protected Integer endPort;

    @Override
    public int compareTo(PortRange o) {
      if (o == null) {
        return 1;
      }

      int res = ObjectUtils.compare(this.startPort, o.startPort);
      return res == 0 ? ObjectUtils.compare(this.endPort, o.endPort) : res;
    }
  }
}
