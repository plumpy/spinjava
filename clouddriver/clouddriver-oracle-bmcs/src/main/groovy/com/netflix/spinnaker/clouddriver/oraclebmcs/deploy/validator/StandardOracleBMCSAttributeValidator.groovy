/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.validator

import org.springframework.validation.Errors

class StandardOracleBMCSAttributeValidator {

  String context
  Errors errors

  StandardOracleBMCSAttributeValidator(String context, Errors errors) {
    this.context = context
    this.errors = errors
  }

  def validateNotEmptyString(String value, String attribute) {
    if (!value) {
      errors.rejectValue(attribute, "${context}.${attribute}.empty")
      return false
    }
    return true
  }
}
