/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.travis.service

import com.netflix.spinnaker.igor.build.model.Result
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class TravisResultConverter {
    static Result getResultFromTravisState(String state) {
        switch (state) {
            case "created":
                return Result.NOT_BUILT
                break
            case "started":
                return Result.BUILDING
                break
            case "passed":
                return Result.SUCCESS
                break
            case "canceled":
                return Result.ABORTED
                break
            case "failed":
                return Result.FAILURE
                break
            case "errored":
                return Result.FAILURE
                break
            default:
                log.info ("could not convert ${state}")
                throw new IllegalArgumentException("state: ${state} is not known to TravisResultConverter.")
                break
        }
    }

    static Boolean running(String state) {
        switch (state) {
            case "created":
                return true
                break
            case "started":
                return true
                break
            default:
                return false
                break
        }
    }
}
