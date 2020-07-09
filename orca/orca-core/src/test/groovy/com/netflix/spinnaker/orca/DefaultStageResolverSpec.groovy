/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spinnaker.orca.api.simplestage.SimpleStage
import com.netflix.spinnaker.orca.api.simplestage.SimpleStageInput
import com.netflix.spinnaker.orca.api.simplestage.SimpleStageOutput
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.WaitStage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DefaultStageResolverSpec extends Specification {
  @Subject
  def stageResolver = new DefaultStageResolver([
    new WaitStage(),
    new AliasedStageDefinitionBuilder()
  ],
    [
      new TestSimpleStage()
    ])

  @Unroll
  def "should lookup stage by name or alias"() {
    expect:
    stageResolver.getStageDefinitionBuilder(stageTypeIdentifier, null).getType() == expectedStageType

    where:
    stageTypeIdentifier || expectedStageType
    "wait"              || "wait"
    "aliased"           || "aliased"
    "notAliased"        || "aliased"
  }

  def "should raise exception on duplicate alias"() {
    when:
    new DefaultStageResolver([
      new AliasedStageDefinitionBuilder(),
      new AliasedStageDefinitionBuilder()
    ],
      [
        new TestSimpleStage()
      ])

    then:
    thrown(StageResolver.DuplicateStageAliasException)
  }

  def "should raise exception when stage not found"() {
    when:
    stageResolver.getStageDefinitionBuilder("DoesNotExist", null)

    then:
    thrown(StageResolver.NoSuchStageDefinitionBuilderException)
  }

  @StageDefinitionBuilder.Aliases("notAliased")
  class AliasedStageDefinitionBuilder implements StageDefinitionBuilder {
  }

  class TestSimpleStage implements SimpleStage {
    @Override
    SimpleStageOutput execute(SimpleStageInput input) {
      return new SimpleStageOutput();
    }

    @Override
    String getName() {
      return "simpleApiStage";
    }
  }
}
