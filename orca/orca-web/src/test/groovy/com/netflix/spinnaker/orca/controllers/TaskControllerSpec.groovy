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

package com.netflix.spinnaker.orca.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.model.JobViewModel
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobInstance
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.item.ExecutionContext
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class TaskControllerSpec extends Specification {

  MockMvc mockMvc
  JobExplorer jobExplorer
  ExecutionRepository executionRepository
  List jobs

  void setup() {
    jobExplorer = Mock(JobExplorer)
    executionRepository = Stub(ExecutionRepository)
    mockMvc = MockMvcBuilders.standaloneSetup(
      new TaskController(jobExplorer: jobExplorer, executionRepository: executionRepository)
    ).build()
    jobs = [
      [instance: new JobInstance(0, 'jobOne'), name: 'jobOne', id: 0],
      [instance: new JobInstance(1, 'jobTwo'), name: 'deploy.jobTwo.randomStuff', id: 1]
    ]
  }

  void '/tasks returns a list of active tasks'() {
    when:
    def response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    jobExplorer.jobNames >> [jobs[0].name, jobs[1].name]
    jobExplorer.findJobInstancesByJobName(jobs[0].name, _, _) >> [jobs[0].instance]
    jobExplorer.findJobInstancesByJobName(jobs[1].name, _, _) >> [jobs[1].instance]
    jobExplorer.getJobExecutions(_) >> { args -> [new JobExecution(args[0], null)] }
    List tasks = new OrcaObjectMapper().readValue(response.contentAsString, List)
    tasks.name == ['jobOne', 'jobTwo'] // make sure they are ordered; they are.
    tasks.size() == 2
  }

  void '/tasks returns tasks sorted by id'() {
    when:
    def response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    jobExplorer.jobNames >> [jobs[1].name, jobs[0].name]
    jobExplorer.findJobInstancesByJobName(jobs[0].name, _, _) >> [jobs[0].instance]
    jobExplorer.findJobInstancesByJobName(jobs[1].name, _, _) >> [jobs[1].instance]
    jobExplorer.getJobExecutions(_) >> { args -> [new JobExecution(args[0], args[0].id, null, null)] }
    List tasks = new ObjectMapper().readValue(response.contentAsString, List)
    tasks.id == [0, 1]
  }

  void 'step names are properly translated'() {
    when:
    def response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    jobExplorer.jobNames >> [jobs[1].name, jobs[0].name]
    jobExplorer.findJobInstancesByJobName(jobs[0].name, _, _) >> [jobs[0].instance]
    jobExplorer.findJobInstancesByJobName(jobs[1].name, _, _) >> [jobs[1].instance]
    jobExplorer.getJobExecutions(_) >> { args -> [new JobExecution(args[0], args[0].id, null, null)] }
    List tasks = new ObjectMapper().readValue(response.contentAsString, List)
    tasks.name == ['jobOne', 'jobTwo']
  }

  void 'stage contexts are included for orchestrated tasks'() {
    setup:
    def orchestration = new Orchestration(id: "1")
    orchestration.stages = [
      new OrchestrationStage(orchestration, "OrchestratedType", ["customOutput": "variable"])
    ]

    when:
    def response = mockMvc.perform(get('/tasks/1')).andReturn().response

    then:
    jobExplorer.getJobExecution(_) >> { args ->
      [new JobExecution(jobs[0].instance, args[0], new JobParameters(["orchestration": orchestration.id]), null)]
    }
    executionRepository.retrieveOrchestration(orchestration.id) >> orchestration

    new ObjectMapper().readValue(response.contentAsString, JobViewModel).variables == [
      [key: "OrchestratedType.customOutput", value: "variable"]
    ]
  }

  void '/tasks returns [] when there are no tasks'() {
    when:
    MockHttpServletResponse response = mockMvc.perform(get('/tasks')).andReturn().response

    then:
    jobExplorer.jobNames >> []
    response.status == 200
    response.contentAsString == '[]'
  }

  void '/applications/{application}/tasks filters tasks by application'() {
    when:
    def response = mockMvc.perform(get("/applications/test/tasks")).andReturn().response

    then:
    jobExplorer.jobNames >> [jobs[0].name, jobs[1].name]
    jobExplorer.findJobInstancesByJobName(jobs[0].name, _, _) >> [jobs[0].instance]
    jobExplorer.findJobInstancesByJobName(jobs[1].name, _, _) >> [jobs[1].instance]
    jobExplorer.getJobExecutions(_) >> { JobInstance jobInstance ->
      def execution = Mock(JobExecution)
      execution.getJobInstance() >> jobInstance
      def pipeline = new Pipeline()
      pipeline.application = application
      if (jobInstance.id == 1) {
        execution.getJobParameters() >> {
          def parameters = Mock(JobParameters)
          parameters.getParameters() >> [:]
          parameters
        }
        execution.getExecutionContext() >> {
          def context = new ExecutionContext()
          context.put('pipeline', pipeline)
          context
        }
      } else {
        execution.getJobParameters() >> {
          def parameters = Mock(JobParameters)
          parameters.getParameters() >> [application: new JobParameter(application, true)]
          parameters
        }
        execution.getExecutionContext() >> new ExecutionContext()
      }
      [execution]
    }
    List tasks = new ObjectMapper().readValue(response.contentAsString, List)
    tasks.size() == 1

    where:
    application = "test"
  }

  void '/pipelines should return a list of pipelines'() {
    when:
    def response = mockMvc.perform(get("/pipelines")).andReturn().response

    then:
    executionRepository.retrievePipelines() >> [new Pipeline(), new Pipeline()]
    List tasks = new ObjectMapper().readValue(response.contentAsString, List)
    tasks.size() == 2
  }
}
