/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.plugins.api.spring

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry

class PrivilegedSpringPluginTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("should register bean definition") {
      plugin.registerBeanDefinitions(registry)
      verify(exactly = 1) { registry.registerBeanDefinition(
        "com.netflix.spinnaker.kork.plugins.api.spring.TestPrivilegedSpringPlugin\$MyService",
        BeanDefinitionBuilder.genericBeanDefinition(TestPrivilegedSpringPlugin.MyService::class.java)
          .setScope(BeanDefinition.SCOPE_SINGLETON)
          .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)
          .getBeanDefinition().also {
            it.isPrimary = true
          }
      )}
    }
  }

  private inner class Fixture {
    val registry: BeanDefinitionRegistry = mockk(relaxed = true)
    val pluginWrapper: PluginWrapper = mockk(relaxed = true)
    val plugin: PrivilegedSpringPlugin = TestPrivilegedSpringPlugin(pluginWrapper)
  }
}
