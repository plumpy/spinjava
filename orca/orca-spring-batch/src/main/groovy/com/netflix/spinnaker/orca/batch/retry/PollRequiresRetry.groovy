/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.retry

import groovy.transform.CompileStatic

/**
 * Thrown by a job step to indicate that the step is polling for completion of
 * some external process that is still running so the step should be re-run
 * after a delay.
 *
 * Yes. This is using exceptions to control program flow. That's how
 * spring-retry works. Sorry.
 */
@CompileStatic
class PollRequiresRetry extends RuntimeException {
  @Override Throwable fillInStackTrace() {
    return this
  }
}
