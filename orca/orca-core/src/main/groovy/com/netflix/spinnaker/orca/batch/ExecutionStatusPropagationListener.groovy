/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL

@Slf4j
@CompileStatic
class ExecutionStatusPropagationListener extends JobExecutionListenerSupport {
  private final ExecutionRepository executionRepository

  ExecutionStatusPropagationListener(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository
  }

  @Override
  void beforeJob(JobExecution jobExecution) {
    def id = executionId(jobExecution)
    executionRepository.updateStatus(id, RUNNING)

    log.info("Marked $id as $RUNNING (beforeJob)")
  }

  @Override
  void afterJob(JobExecution jobExecution) {
    def stepExecutions = new ArrayList<StepExecution>(jobExecution.stepExecutions).sort { it.lastUpdated }.reverse()
    def stepExecution = stepExecutions.find { it.status == jobExecution.status } ?: stepExecutions[0]
    def orcaTaskStatus = stepExecution?.executionContext?.get("orcaTaskStatus") as ExecutionStatus ?: TERMINAL

    def id = executionId(jobExecution)
    executionRepository.updateStatus(id, orcaTaskStatus)

    log.info("Marked $id as $orcaTaskStatus (afterJob)")
  }

  private String executionId(JobExecution jobExecution) {
    if (jobExecution.jobParameters.getString("pipeline")) {
      return jobExecution.jobParameters.getString("pipeline")
    }
    return jobExecution.jobParameters.getString("orchestration")
  }
}
