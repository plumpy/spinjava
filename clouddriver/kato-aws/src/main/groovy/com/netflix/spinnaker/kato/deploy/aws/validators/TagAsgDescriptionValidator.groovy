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


package com.netflix.spinnaker.kato.deploy.aws.validators

import com.netflix.spinnaker.kato.deploy.aws.description.TagAsgDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("tagAsgDescriptionValidator")
class TagAsgDescriptionValidator extends AmazonDescriptionValidationSupport<TagAsgDescription> {
  @Override
  void validate(List priorDescriptions, TagAsgDescription description, Errors errors) {
    validateAsgNameAndRegions description, errors
    if (!description.tags) {
      errors.rejectValue("tags", "tagAsgDescription.tags.empty")
    }
    description.tags.each { k, v ->
      if (!k || !v) {
        errors.rejectValue("tags", "tagAsgDescription.tag.invalid")
      }
    }
  }
}
