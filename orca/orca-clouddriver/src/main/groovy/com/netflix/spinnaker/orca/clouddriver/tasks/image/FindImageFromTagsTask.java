/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.DefaultTaskResult;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FindImageFromTagsTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  List<ImageFinder> imageFinders;

  @Override
  public TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage);
    ImageFinder imageFinder = imageFinders.stream()
      .filter(it -> it.getCloudProvider().equals(cloudProvider))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("ImageFinder not found for cloudProvider " + cloudProvider));

    StageData stageData = (StageData) stage.mapTo(StageData.class);
    return new DefaultTaskResult(
      ExecutionStatus.SUCCEEDED,
      Collections.singletonMap("amiDetails", imageFinder.byTags(stage, stageData.packageName, stageData.tags))
    );
  }

  @Override
  public long getBackoffPeriod() {
    return 10000;
  }

  @Override
  public long getTimeout() {
    return 600000;
  }

  static class StageData {
    @JsonProperty
    String packageName;

    @JsonProperty
    Map<String, String> tags;
  }
}
