/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.loader.FileTemplateSchemeLoader
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.YamlRenderedValueConverter
import org.unitils.reflectionassert.ReflectionComparatorMode
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.unitils.reflectionassert.ReflectionAssert.assertReflectionEquals

class PipelineTemplatePipelinePreprocessorSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  TemplateLoader templateLoader = new TemplateLoader([new FileTemplateSchemeLoader(objectMapper)])

  Renderer renderer = new JinjaRenderer(
    new YamlRenderedValueConverter(new Yaml()), objectMapper, Mock(Front50Service), []
  )

  Registry registry = Mock() {
    clock() >> Mock(Clock) {
      monotonicTime() >> 0L
    }
    timer(_) >> Mock(Timer)
  }

  @Subject
  PipelineTemplatePipelinePreprocessor subject = new PipelineTemplatePipelinePreprocessor(
    objectMapper,
    templateLoader,
    renderer,
    registry
  )

  def 'should ignore non-templated pipeline requests'() {
    given:
    def request = [
      type: 'not-interested'
    ]

    when:
    def result = subject.process(request)

    then:
    result == [
      type: 'not-interested'
    ]
    0 * _
  }

  def 'should process simple template'() {
    given:
    def request = createTemplateRequest('simple-001.yml', [
      regions: ['us-east-1', 'us-west-2']
    ], [
      [
        id: 'wait',
        type: 'wait',
        dependsOn: ['tagImage'],
        config: [waitTime: 5]
      ],
      [
        id: 'injectedWait',
        type: 'wait',
        inject: [
          first: true
        ],
        config: [
          waitTime: 5
        ]
      ]
    ])

    when:
    def result = subject.process(request)

    then:
    def expected = [
      id: null,
      application: 'myapp',
      name: 'Unnamed Execution',
      keepWaitingPipelines: false,
      limitConcurrent: true,
      notifications: [],
      stages: [
        [
          id: null,
          refId: 'injectedWait',
          requisiteStageRefIds: [],
          type: 'wait',
          name: 'injectedWait',
          waitTime: 5
        ],
        [
          id: null,
          refId: 'bake',
          type: 'bake',
          name: 'Bake',
          requisiteStageRefIds: ['injectedWait'],
          regions: ['us-east-1', 'us-west-2'],
          package: 'myapp-package',
          baseOs: 'trusty',
          vmType: 'hvm',
          storeType: 'ebs',
          baseLabel: 'release'
        ],
        [
          id: null,
          refId: 'tagImage',
          type: 'tagImage',
          name: 'Tag Image',
          requisiteStageRefIds: ['bake'],
          tags: [
            stack: 'test'
          ]
        ],
        [
          id: null,
          refId: 'wait',
          type: 'wait',
          name: 'wait',
          requisiteStageRefIds: ['tagImage'],
          waitTime: 5
        ]
      ]
    ]
    assertReflectionEquals(expected, result, ReflectionComparatorMode.IGNORE_DEFAULTS)
  }

  def 'should render jackson mapping exceptions'() {
    when:
    def result = subject.process(createTemplateRequest('invalid-template-001.yml', [:], [], true))

    then:
    noExceptionThrown()
    result.errors != null
    result.errors*.message.contains("failed loading template")
  }

  @Unroll
  def 'should not render falsy conditional stages'() {
    when:
    def result = subject.process(createTemplateRequest('conditional-stages-001.yml', [includeWait: includeWait]))

    then:
    result.stages*.name == expectedStageNames

    where:
    includeWait || expectedStageNames
    true        || ['wait', 'conditionalWait']
    false       || ['wait']
  }

  @Unroll
  def 'should preserve children stages of conditional stage'() {
    when:
    def result = subject.process(createTemplateRequest('conditional-stages-with-children-001.yml', [includeWait: includeWait]))

    then:
    result.stages*.name == expectedStageNames

    and:
    result.stages.find { it.name == 'childOfConditionalStage' }.requisiteStageRefIds == childStageRequisiteRefIds as Set

    where:
    includeWait || childStageRequisiteRefIds  || expectedStageNames
    true        || ['conditionalWait']        || ['wait', 'conditionalWait', 'childOfConditionalStage']
    false       || ['wait']                   || ['wait', 'childOfConditionalStage']
  }

  @Unroll
  def 'should be able to set source using jinja'() {
    when:
    def result = subject.process(createInjectedTemplateRequest(template))

    then:
    result.stages*.name == expectedStageNames

    where:
    template        || expectedStageNames
    'jinja-001.yml' || ['jinja1']
    'jinja-002.yml' || ['jinja2']
  }

  def 'should allow inlined templates during plan'() {
    when:
    def result = subject.process(createInlinedTemplateRequest(true))

    then:
    noExceptionThrown()
    result.stages*.name == ['wait']

    when:
    result = subject.process(createInlinedTemplateRequest(false))

    then:
    result.errors != null
  }

  def 'should load parent templates of inlined template during plan'() {
    when:
    def result = subject.process(createInlinedTemplateRequestWithParent(true, 'jinja-001.yml'))

    then:
    noExceptionThrown()
    result.stages*.name == ['jinja1', 'childTemplateWait']

    when:
    result = subject.process(createInlinedTemplateRequestWithParent(false, 'jinja-001.yml'))

    then:
    result.errors != null
  }

  @Unroll
  def 'should render jinja expressions contained within template variables'() {
    given:
    def pipelineTemplate = new PipelineTemplate(variables: templateVariables.collect {
      new PipelineTemplate.Variable(name: it.key, defaultValue: it.value)
    })

    def templateConfig = new TemplateConfiguration(
      pipeline: new TemplateConfiguration.PipelineDefinition(variables: configVariables)
    )

    def renderContext = RenderUtil.createDefaultRenderContext(
      pipelineTemplate, templateConfig, [
      parameters: [
        "list"   : "us-west-2,us-east-1",
        "boolean": "true",
        "string" : "this is a string"
      ]
    ])

    when:
    subject.renderTemplateVariables(renderContext, pipelineTemplate)

    then:
    pipelineTemplate.variables*.defaultValue == expectedDefaultValues

    where:
    templateVariables                                                     | configVariables       || expectedDefaultValues
    [key1: "string1", key2: "string2"]                                    | [:]                   || ["string1", "string2"]
    [key1: "{{ trigger.parameters.string }}", key2: "string2"]            | [:]                   || ["this is a string", "string2"]
    [key1: "{{ trigger.parameters.list | split(',') }}", key2: "string2"] | [:]                   || [["us-west-2", "us-east-1"], "string2"]
    [key1: "string1", key2: "{{ key1 }}"]                                 | [:]                   || ["string1", "string1"]
    [key2: "{{ key1 }}"]                                                  | [key1: "string1"]     || ["string1"]
  }

  def "should include group for partials-generated stages"() {
    def pipeline = [
      type: 'templatedPipeline',
      config: [
        schema: '1',
        pipeline: [
          application: 'myapp'
        ]
      ],
      template: [
        schema: '1',
        id: 'myTemplate',
        configuration: [:],
        partials: [
          [
            id: 'mypartial',
            name: 'my group of stages',
            stages: [
              new StageDefinition(
                id: 'wait',
                type: 'wait',
                requisiteStageRefIds: [] as Set,
                config: [
                  waitTime: 5
                ]
              ),
              new StageDefinition(
                id: 'wait2',
                type: 'wait',
                requisiteStageRefIds: [] as Set,
                config: [
                  waitTime: 5
                ]
              )
            ]
          ]
        ],
        stages: [
          [
            id: 'waiting',
            name: 'wowow waiting',
            type: 'partial.mypartial',
            config: [:]
          ]
        ]
      ],
      plan: true
    ]

    when:
    def result = subject.process(pipeline)

    then:
    result.stages*.group == ['my group of stages: wowow waiting', 'my group of stages: wowow waiting']
  }

  @Unroll
  def 'should not render falsy conditional stages inside partials'() {
    when:
    def template =  createTemplateRequest('conditional-partials.yml', [includeWait: includeWait])
    def result = subject.process(template)

    then:
    result.stages*.name == expectedStageNames

    where:
    includeWait || expectedStageNames
    true        || ['stageWithPartialsAndConditional.wait', 'stageWithPartialsAndConditional.conditionalWaitOnPartial']
    false       || ['stageWithPartialsAndConditional.wait']
  }

  def "should respect request-defined concurrency options if configuration does not define them"() {
    given:
    def pipeline = [
      type: 'templatedPipeline',
      config: [
        schema: '1',
        pipeline: [
          application: 'myapp'
        ]
      ],
      template: [
        schema: '1',
        id: 'myTemplate',
        configuration: [:],
        stages: []
      ],
      plan: true,
      limitConcurrent: false
    ]

    when:
    def result = subject.process(pipeline)

    then:
    result.limitConcurrent == false
  }

  Map<String, Object> createTemplateRequest(String templatePath, Map<String, Object> variables = [:], List<Map<String, Object>> stages = [], boolean plan = false) {
    return [
      type: 'templatedPipeline',
      trigger: [
        type: "jenkins",
        master: "master",
        job: "job",
        buildNumber: 1111
      ],
      config: [
        schema: '1',
        pipeline: [
          application: 'myapp',
          template: [
            source: getClass().getResource("/templates/${templatePath}").toURI()
          ],
          variables: variables
        ],
        stages: stages
      ],
      plan: plan
    ]
  }

  Map<String, Object> createInjectedTemplateRequest(String templatePath) {
    return [
      type: 'templatedPipeline',
      trigger: [
        parameters: [
          template: getClass().getResource("/templates/${templatePath}").toURI()
        ]
      ],
      config: [
        schema: '1',
        pipeline: [
          application: 'myapp',
          template: [
            source: '{{trigger.parameters.template}}'
          ],
        ],
      ],
      plan: false
    ]
  }

  Map<String, Object> createInlinedTemplateRequest(boolean plan) {
    return [
      type: 'templatedPipeline',
      config: [
        schema: '1',
        pipeline: [
          application: 'myapp'
        ]
      ],
      template: [
        schema: '1',
        id: 'myTemplate',
        stages: [
          [
            id: 'wait',
            type: 'wait',
            config: [
              waitTime: 5
            ]
          ]
        ]
      ],
      plan: plan
    ]
  }

  Map<String, Object> createInlinedTemplateRequestWithParent(boolean plan, String templatePath) {
    return [
      type: 'templatedPipeline',
      config: [
        schema: '1',
        pipeline: [
          application: 'myapp'
        ]
      ],
      template: [
        schema: '1',
        id: 'myTemplate',
        stages: [
          [
            id: 'childTemplateWait',
            type: 'wait',
            config: [
              waitTime: 5
            ],
            inject: [
              last: true
            ]
          ]
        ],
        source: getClass().getResource("/templates/${templatePath}").toURI()
      ],
      plan: plan,
    ]
  }
}
