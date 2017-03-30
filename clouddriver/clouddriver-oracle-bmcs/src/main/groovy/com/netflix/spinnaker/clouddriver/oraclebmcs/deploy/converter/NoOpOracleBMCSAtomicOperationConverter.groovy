/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.converter

import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

/**
 * SL: Adding this for initial pull request to satisfy FeatureController requirement of at least
 * one atomic operation enabled when starting clouddriver with just oraclebmcs enabled.
 */
@OracleBMCSOperation(AtomicOperations.DELETE_SECURITY_GROUP)
@Component("noopOracleBMCSDescription")
class NoOpOracleBMCSAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    return null
  }

  @Override
  Object convertDescription(Map input) {
    return null
  }
}
