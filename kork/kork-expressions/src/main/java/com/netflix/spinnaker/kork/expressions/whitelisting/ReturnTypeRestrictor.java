/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.kork.expressions.whitelisting;

import java.util.Set;

public class ReturnTypeRestrictor extends InstantiationTypeRestrictor {
  private final Set<Class<?>> allowedReturnTypes;

  public ReturnTypeRestrictor(Set<Class<?>> allowedReturnTypes) {
    this.allowedReturnTypes = allowedReturnTypes;
  }

  @Override
  boolean supports(Class<?> type) {
    Class<?> returnType = type.isArray() ? type.getComponentType() : type;
    return returnType.isPrimitive() || super.supports(returnType) || allowedReturnTypes.contains(returnType);
  }
}
