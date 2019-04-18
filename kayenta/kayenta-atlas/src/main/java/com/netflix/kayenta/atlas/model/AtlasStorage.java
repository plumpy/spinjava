/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.kayenta.atlas.model;

import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AtlasStorage {

  @NotNull
  @Getter
  private String global;

  @NotNull
  @Getter
  private String regional;

  @NotNull
  @Getter
  private List<String> regions;

  public Optional<String> getRegionalCnameForRegion(String region) {
    if (regions.contains(region)) {
      return Optional.of(regional.replace("$(region)", region));
    } else {
      return Optional.empty();
    }
  }
}
