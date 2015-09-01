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

package com.netflix.spinnaker.kato.orchestration
import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.kato.orchestration.testregistry.TestConverter
import com.netflix.spinnaker.kato.orchestration.testregistry.TestProvider
import com.netflix.spinnaker.kato.orchestration.testregistry.TestValidator
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import spock.lang.Specification
import spock.lang.Subject

import java.lang.annotation.Annotation
/**
 * @author sthadeshwar
 */
class AnnotatedAtomicOperationsRegistrySpec extends Specification {

  @Subject
  AtomicOperationsRegistry annotatedAtomicOperationsRegistry = new AnnotatedAtomicOperationsRegistry(
    applicationContext: new AnnotationConfigApplicationContext(TestConfiguration),
    cloudProviders: [new MyCloudProvider()]
  )

  void 'annotations based registry should return the converter if the specified name matches the component name'() {
    when:
    def converter = annotatedAtomicOperationsRegistry.getAtomicOperationConverter('operationOldDescription', null)

    then:
    noExceptionThrown()
    converter != null
    converter instanceof TestConverter
  }

  void 'annotations based registry should return the converter that matches the AtomicOperationDescription name and cloud provider'() {
    when:
    def converter = annotatedAtomicOperationsRegistry.getAtomicOperationConverter('operationDescription', 'test-provider')

    then:
    noExceptionThrown()
    converter != null
    converter instanceof TestConverter
  }

  void 'annotations based registry should throw a NoSuchBeanDefinitionException if no converter found for given name with no cloud provider specified'() {
    when:
    def converter = annotatedAtomicOperationsRegistry.getAtomicOperationConverter('foo', null)

    then:
    thrown(NoSuchBeanDefinitionException)
    converter == null
  }

  void 'annotations based registry should throw an AtomicOperationConverterNotFoundException if no converter found for given name with cloud provider specified'() {
    when:
    def converter = annotatedAtomicOperationsRegistry.getAtomicOperationConverter('foo', 'test-provider')

    then:
    thrown(AtomicOperationConverterNotFoundException)
    converter == null
  }

  void 'annotations based registry should return the validator if the specified name matches the component name'() {
    when:
    def validator = annotatedAtomicOperationsRegistry.getAtomicOperationDescriptionValidator('operationOldDescriptionValidator', null)

    then:
    noExceptionThrown()
    validator != null
    validator instanceof TestValidator
  }

  void 'annotations based registry should return the validator that matches the AtomicOperationDescription name and cloud provider'() {
    when:
    def validator = annotatedAtomicOperationsRegistry.getAtomicOperationDescriptionValidator('operationDescriptionValidator', 'test-provider')

    then:
    noExceptionThrown()
    validator != null
    validator instanceof TestValidator
  }

  void 'annotations based registry should return a null if no validator found for given name with no cloud provider specified'() {
    when:
    def validator = annotatedAtomicOperationsRegistry.getAtomicOperationDescriptionValidator('foo', null)

    then:
    noExceptionThrown()
    validator == null
  }

  void 'annotations based registry should return a null if no validator found for given name with cloud provider specified'() {
    when:
    def validator = annotatedAtomicOperationsRegistry.getAtomicOperationDescriptionValidator('foo', 'test-provider')

    then:
    noExceptionThrown()
    validator == null
  }

  static class MyCloudProvider implements CloudProvider {
    String id = 'test-provider'
    String displayName = 'Test Provider'
    Class<? extends Annotation> annotation = TestProvider
  }

  @Configuration
  @ComponentScan(["com.netflix.spinnaker.kato.orchestration.testregistry"])
  static class TestConfiguration {}

}
