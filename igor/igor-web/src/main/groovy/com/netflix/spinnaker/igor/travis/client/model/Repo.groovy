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

package com.netflix.spinnaker.igor.travis.client.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.google.gson.annotations.SerializedName
import groovy.transform.CompileStatic
import org.simpleframework.xml.Default
import org.simpleframework.xml.Root

@Default
@CompileStatic
@JsonInclude(JsonInclude.Include.NON_NULL)
@Root(strict = false)
class Repo {
    int id
    String slug
    String description
    @SerializedName("last_build_id")
    int lastBuildId
    @SerializedName("last_build_number")
    int lastBuildNumber
    @SerializedName("last_build_state")
    String lastBuildState
    @SerializedName("last_build_duration")
    int lastBuildDuration
    @SerializedName("last_build_started_at")
    Date lastBuildStartedAt
    @SerializedName("last_build_finished_at")
    Date lastBuildFinishedAt
    @SerializedName("github_language")
    String githubLanguage

    long timestamp() {
        return lastBuildFinishedAt.getTime()
    }
}
