package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.StartServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

public class StartServiceAtomicOperation implements AtomicOperation<Void> {

  StartServiceDescription description;

  public StartServiceAtomicOperation(StartServiceDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {

    // TODO - implement this stub

    return null;
  }

}
