/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.orca.batch.ExecutionListenerProvider;
import com.netflix.spinnaker.orca.batch.OrchestrationInitializerTasklet;
import com.netflix.spinnaker.orca.listeners.CompositeExecutionListener;
import groovy.transform.CompileStatic;
import com.netflix.spinnaker.orca.pipeline.model.Orchestration;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.JobFlowBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@CompileStatic
public class OrchestrationJobBuilder extends ExecutionJobBuilder<Orchestration> {

  private final ExecutionListenerProvider executionListenerProvider;
  private final CompositeExecutionListener compositeExecutionListener;

  @Autowired
  public OrchestrationJobBuilder(ExecutionListenerProvider executionListenerProvider,
                                 CompositeExecutionListener compositeExecutionListener) {
    this.executionListenerProvider = executionListenerProvider;
    this.compositeExecutionListener = compositeExecutionListener;
  }

  @Override
  public Job build(Orchestration orchestration) {
    String name = jobNameFor(orchestration);
    JobBuilder jobBuilder = jobs.get(name);
    jobBuilder = jobBuilder.listener(executionListenerProvider.wrap(compositeExecutionListener));

    Step tasklet = new OrchestrationInitializerTasklet(orchestration).createTasklet(steps);
    JobFlowBuilder jobFlowBuilder = jobBuilder.flow(tasklet);

    List<Stage<Orchestration>> orchestrationStages = new ArrayList<>(orchestration.getStages());
    for (Stage stage : orchestrationStages) {
      stages.get(stage.getType()).build(jobFlowBuilder, stage);
    }

    return jobFlowBuilder.build().build();
  }

  @Override
  public String jobNameFor(Orchestration orchestration) {
    return String.format("Orchestration:%s", orchestration.getId());
  }
}
