/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.securitygroup;

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.MigrateSecurityGroupTask;
import com.netflix.spinnaker.orca.pipeline.LinearStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.batch.core.Step;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MigrateSecurityGroupStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "migrateLoadBalancer";

  MigrateSecurityGroupStage() {
    super(PIPELINE_CONFIG_TYPE);
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    List<Step> steps = new ArrayList<>();
    steps.add(buildStep(stage, "migrateSecurityGroup", MigrateSecurityGroupTask.class));
    steps.add(buildStep(stage, "monitorMigration", MonitorKatoTask.class));
    return steps;
  }
}
