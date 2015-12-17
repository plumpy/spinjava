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

package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import java.util.concurrent.TimeUnit

@Slf4j
@Component
class MonitorQueuedJenkinsJobTask implements RetryableTask {

  long backoffPeriod = 1000
  long timeout = TimeUnit.HOURS.toMillis(2)

  @Autowired
  BuildService buildService

  @Override
  TaskResult execute(Stage stage) {
    String master = stage.context.master
    String job = stage.context.job
    String queuedBuild = stage.context.queuedBuild

    try {
      Map<String, Object> build = buildService.queuedBuild(master, queuedBuild)
      if (build?.number == null) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      } else {
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [buildNumber: build.number])
      }
    } catch (RetrofitError e) {
      if ([503, 500, 404].contains(e.response?.status)) {
        log.warn("Http ${e.response.status} received from `igor`, retrying...")
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      }
      throw e
    }
  }
}
