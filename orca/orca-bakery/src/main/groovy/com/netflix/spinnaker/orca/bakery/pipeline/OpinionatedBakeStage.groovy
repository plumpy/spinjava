/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.pipeline

import com.netflix.spinnaker.orca.bakery.tasks.PreconfigureOpinionatedBake
import com.netflix.spinnaker.orca.pipeline.LinearStage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class OpinionatedBakeStage extends LinearStage {
  private static final String MAYO_NAME = "opinionatedBake"

  @Autowired
  BakeStage bakeStage

  OpinionatedBakeStage() {
    super(MAYO_NAME)
  }

  List<Step> buildSteps() {
    def step1 = steps.get("PreconfigureOpinionatedBake")
                    .tasklet(buildTask(PreconfigureOpinionatedBake))
                    .build()
    def restOfSteps = bakeStage.buildSteps()
    [step1, restOfSteps].flatten()
  }
}
