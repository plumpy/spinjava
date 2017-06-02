/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.stackdriver.orca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.stackdriver.query.StackdriverQuery;
import com.netflix.kayenta.stackdriver.query.StackdriverSynchronousQueryProcessor;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

@Component
public class StackdriverFetchTask implements RetryableTask {

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  StackdriverSynchronousQueryProcessor stackdriverSynchronousQueryProcessor;

  @Override
  public long getBackoffPeriod() {
    // TODO(duftler): Externalize this configuration.
    return Duration.ofSeconds(2).toMillis();
  }

  @Override
  public long getTimeout() {
    // TODO(duftler): Externalize this configuration.
    return Duration.ofMinutes(2).toMillis();
  }

  @Override
  public TaskResult execute(Stage stage) {
    StackdriverQuery stackdriverQuery =
      objectMapper.convertValue(stage.getContext().get("stackdriverQuery"), StackdriverQuery.class);

    try {
      String metricSetListId = stackdriverSynchronousQueryProcessor.processQuery(stackdriverQuery);
      Map outputs = Collections.singletonMap("metricSetListId", metricSetListId);

      return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
