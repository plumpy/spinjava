/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins

import com.netflix.spinnaker.kork.plugins.events.ExtensionLoaded
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.pf4j.ExtensionFactory
import org.pf4j.ExtensionPoint
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.support.GenericApplicationContext

class ExtensionBeanDefinitionRegistryPostProcessorTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("post process bean definition registry") {
      test("plugin manager loads and starts plugins") {
        subject.postProcessBeanDefinitionRegistry(GenericApplicationContext())

        verify(exactly = 1) { pluginManager.loadPlugins() }
        verify(exactly = 1) { pluginManager.startPlugins() }
      }
    }

    context("post process bean factory") {
      test("system extensions are injected into parent Spring registry") {
        every { pluginManager.getExtensionClassNames(null) } returns setOf(
          FooExtension::class.java.name
        )
        every { pluginManager.getExtensionClassNames(eq("testSpringPlugin")) } returns setOf()

        val beanFactory: ConfigurableListableBeanFactory = mockk(relaxed = true)

        subject.postProcessBeanFactory(beanFactory)

        verify(exactly = 1) { extensionFactory.create(eq(FooExtension::class.java)) }
        verify(exactly = 1) { beanFactory.registerSingleton(eq("fooExtensionSystemExtension"), any<FooExtension>()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ExtensionLoaded>()) }
      }

      test("plugin extensions are injected into parent Spring registry") {
        every { pluginManager.getExtensionClassNames(null) } returns setOf()
        every { pluginManager.startedPlugins } returns listOf(pluginWrapper)
        every { pluginManager.getExtensionClassNames(eq("testSpringPlugin")) } returns setOf(
          FooExtension::class.java.name
        )

        val beanFactory: ConfigurableListableBeanFactory = mockk(relaxed = true)

        subject.postProcessBeanFactory(beanFactory)

        verify(exactly = 1) { extensionFactory.create(eq(FooExtension::class.java)) }
        verify(exactly = 1) { beanFactory.registerSingleton(eq("testSpringPluginFooExtension"), any<FooExtension>()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ExtensionLoaded>()) }
      }
    }
  }

  private class Fixture {
    val pluginManager: SpinnakerPluginManager = mockk(relaxed = true)
    val pluginWrapper: PluginWrapper = mockk(relaxed = true)
    val extensionFactory: ExtensionFactory = mockk(relaxed = true)
    val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)

    val subject = ExtensionBeanDefinitionRegistryPostProcessor(pluginManager, applicationEventPublisher)

    init {
      every { extensionFactory.create(eq(FooExtension::class.java)) } returns FooExtension()
      every { pluginWrapper.pluginClassLoader } returns javaClass.classLoader
      every { pluginWrapper.plugin } returns TestSpringPlugin(pluginWrapper)
      every { pluginWrapper.pluginId } returns "testSpringPlugin"
      every { pluginWrapper.descriptor } returns mockk<SpinnakerPluginDescriptor>()
      every { pluginManager.extensionFactory } returns extensionFactory
    }
  }

  @SpinnakerExtension(namespace = "netflix", id = "foo")
  private class FooExtension : ExampleExtensionPoint, ConfigurableExtension<FooExtension.FooExtensionConfig> {
    lateinit var config: FooExtensionConfig

    override fun setConfiguration(configuration: FooExtensionConfig) {
      config = configuration
    }

    class FooExtensionConfig
  }

  private interface ExampleExtensionPoint : ExtensionPoint
}
