/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kato.cf.deploy.validators

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import org.springframework.validation.Errors

/**
 * Common validation routines for standard description attributes.
 *
 * This is a helper class intended to be instantiated once per data object being validated
 * and used to validate several attributes. This validator instance keeps the accumulated
 * errors and {@code context} decorator used for encountered error strings.
 *
 * The individual type validation methods return {@code true} on success and {@code false} on error.
 * Error descriptions are added into the {@code errors} object bound at construction. When errors
 * are encountered, they are normally rejected with a message in the form {@code context.attribute.policy}
 * where:
 * <ul>
 *   <li> {@code context} is the descriptor owning the attribute.
 *   <li> {@code attribute} is the name of the particular attribute whose validation failed.
 *        In the event of repeated attributes (e.g. a list) then this name
 *        is decorated with the index of the specific instance.
 *   <li> {@code policy} indicates the policy that was violated causing the error.
 * </ul>
 *
 * Standard policies are currently:
 * <ul>
 *   <li> {@code "empty"} indicates an empty (or null) string or list value.
 *   <li> {@code "invalid"} indicates some other error.
 *   <li> {@code "negative"} integer was negative.
 * </ul>
 *
 * Note that when a validation is rejected, {@code Errors.rejectValue} is called with the name of the
 * attribute that failed, and the policy message described above.
 *
 */
class StandardCfAttributeValidator {
  /**
   * Bound at construction, contains the name of the type being validated used to decorate errors.
   */
  String context

  /**
   * Bound at construction, this is used to collect validation errors.
   */
  Errors errors

  /**
   * Constructs validator for standard attributes added by CF.
   *
   * @param context The owner of the attributes to be validated is typically a {@code *Description} class.
   * @param errors  Accumulates and reports on the validation errors over the lifetime of this validator.
   */
  StandardCfAttributeValidator(String context, Errors errors) {
    this.context = context
    this.errors = errors
  }

  /**
   * Validate a value that should not be empty.
   *
   * @param value The value cannot be null, empty string, or an empty container.
   *              However it can be a container of only empty values.
   * @param attribute The attribute and part names are the same, meaning this is
   *              suitable for 'primitive' values or composite types treated as a whole.
   *
   * @see validateNotEmptyAsPart
   */
  def validateNotEmpty(Object value, String attribute) {
    validateNotEmptyAsPart(value, attribute, attribute)
  }

  /**
   * Validate a value that should not be empty.
   *
   * @param value The value cannot be null, empty string, or an empty container.
   *              However it can be a container of only empty values.
   * @param attribute The name of the attribute being validated.
   * @param part If different than the attribute name then this is a subcomponent
   *             within the attribute (e.g. element within a list).
   */
  def validateNotEmptyAsPart(Object value, String attribute, String part) {
    if (!value) {
      errors.rejectValue(attribute, "${context}.${part}.empty")
      return false
    }
    return true
  }

  /**
   * Validates {@code value} as a valid generic CF name.
   *
   * @param value The name being validated.
   *              However it can be a container of only empty values.
   * @param attribute The attribute and part names are the same, meaning this is
   *              suitable for 'primitive' values or composite types treated as a whole.
   *
   * @see validateNameAsPart
   */
  def validateName(String value, String attribute) {
    validateNameAsPart(value, attribute, attribute)
  }

  /**
   * Validates {@code value} as a valid generic CF name.
   *
   * Specific resource types may have more specific validation rules.
   * This validator is just treating {@code value} as a generic CF name,
   * which cannot be empty and may have additional constraints (symbols, etc).
   *
   * @param value The name being validated.
   * @param attribute The name of the attribute being validated.
   * @param part If different than the attribute name then this is a subcomponent
   *             within the attribute (e.g. element within a list).
   */
  def validateNameAsPart(String value, String attribute, String part) {
    def result = validateNotEmptyAsPart(value, attribute, part)

    // TODO(ewiseblatt): 20150323
    // Add additional rules here, if there are standard CF naming rules.
    //
    // (e.g. see https://cloud.google.com/compute/docs/load-balancing/network/forwarding-rules#forwarding_rule_properties)
    // says the name must be 1-63 chars long and match the RE [a-z]([-a-z0-9]*[a-z0-9])?
    // However, I dont notice these explicit constraints documented for all naming context.
    return result
  }

  def validateCredentials(String accountName, AccountCredentialsProvider accountCredentialsProvider) {
    def result = validateNotEmpty(accountName, "credentials")
    if (result) {
      def credentials = accountCredentialsProvider.getCredentials(accountName)

      if (!(credentials instanceof CloudFoundryAccountCredentials)) {
        errors.rejectValue("credentials", "${context}.credentials.invalid")
        result = false
      }
    }
    return result
  }

  // TODO(duftler): Also validate against set of supported CF regions.
  def validateRegion(String region) {
    validateNotEmpty(region, "region")
  }

  // TODO(duftler): Also validate against set of supported CF zones.
  def validateZone(String zone) {
    validateNotEmpty(zone, "zone")
  }

  def validateNetwork(String network) {
    validateNotEmpty(network, "network")
  }

  def validateNonNegativeInt(int value, String attribute) {
    def result = true
    if (value < 0) {
      errors.rejectValue attribute, "${context}.${attribute}.negative"
      result = false
    }
    return result
  }

  def validateServerGroupName(String serverGroupName) {
    validateName(serverGroupName, "serverGroupName")
  }

  // TODO(duftler): Also validate against set of supported CF images.
  def validateImage(String image) {
    validateNotEmpty(image, "image")
  }

  def validateNameList(List<String> names, String componentDescription) {
    validateNameListHelper(names, componentDescription, false)
  }

  def validateOptionalNameList(List<String> names, String componentDescription) {
    validateNameListHelper(names, componentDescription, true)
  }

  def validateNameListHelper(List<String> names, String componentDescription, boolean emptyListIsOk) {
    if (emptyListIsOk && !names) {
      return true
    }

    def result = validateNotEmpty(names, "${componentDescription}s")
    names.eachWithIndex { value, index ->
      result &= validateNameAsPart(value, "${componentDescription}s", "$componentDescription$index")
    }
    return result
  }

  def validateInstanceIds(List<String> instanceIds) {
    return validateNameList(instanceIds, "instanceId")
  }

  def validateInstanceName(String instanceName) {
    validateName(instanceName, "instanceName")
  }

  // TODO(duftler): Also validate against set of supported CF types.
  def validateInstanceType(String instanceType) {
    validateNotEmpty(instanceType, "instanceType")
  }

  def validateTags(List<String> tags) {
    return validateOptionalNameList(tags, "tag")
  }
}
