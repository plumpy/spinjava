/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors

import com.netflix.spinnaker.echo.test.RetrofitStubs
import spock.lang.Specification
import spock.lang.Subject

class ArtifactPostProcessorSpec extends Specification implements RetrofitStubs {
  @Subject
  def artifactPostProcessor = new ArtifactPostProcessor()

  def "is (currently) a no-op"() {
    given:
    def inputPipeline = createPipelineWith(enabledJenkinsTrigger)

    when:
    def outputPipeline = artifactPostProcessor.processPipeline(inputPipeline)

    then:
    outputPipeline.id == inputPipeline.id
  }
}
