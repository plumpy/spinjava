/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description

import groovy.transform.AutoClone
import groovy.transform.Canonical
import groovy.transform.ToString

@AutoClone
@Canonical
@ToString(includeNames = true)
class BaseOracleBMCSInstanceDescription extends AbstractOracleBMCSCredentialsDescription {

  String imageId
  String shape
  String region
  String availabilityDomain
  String vpcId
  String subnetId
  String accountName

}
