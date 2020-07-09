package com.netflix.spinnaker.orca.controllers;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static java.util.stream.Collectors.toList;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CorrelatedTasksController {

  private final ExecutionRepository executionRepository;

  @Autowired
  public CorrelatedTasksController(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository;
  }

  @GetMapping(path = "/executions/correlated/{correlationId}", produces = APPLICATION_JSON_VALUE)
  public List<String> getCorrelatedExecutions(@PathVariable String correlationId) {
    return Stream.<PipelineExecution>builder()
        .add(getCorrelated(PIPELINE, correlationId))
        .add(getCorrelated(ORCHESTRATION, correlationId))
        .build()
        .filter(Objects::nonNull)
        .map(PipelineExecution::getId)
        .collect(toList());
  }

  private PipelineExecution getCorrelated(ExecutionType executionType, String correlationId) {
    try {
      return executionRepository.retrieveByCorrelationId(executionType, correlationId);
    } catch (ExecutionNotFoundException ignored) {
      return null;
    }
  }
}
