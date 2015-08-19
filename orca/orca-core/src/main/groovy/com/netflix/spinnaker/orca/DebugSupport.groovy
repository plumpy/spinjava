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

package com.netflix.spinnaker.orca

import org.apache.commons.collections.MapUtils

/**
 * Utility class that aids in debugging Maps in the logs.
 *
 * Created by ttomsu on 8/20/15.
 */
class DebugSupport {

  /**
   * @return a prettier, loggable string version of a Map.
   */
  static String prettyPrint(Map m) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    MapUtils.debugPrint(ps, null, m);
    return baos.toString();
  }
}
