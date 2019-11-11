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

import com.netflix.spinnaker.config.PluginsAutoConfiguration
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import strikt.api.expect
import strikt.assertions.isA

class PluginSystemTest : JUnit5Minutests {

  fun tests() = rootContext<ApplicationContextRunner> {
    fixture {
      ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
          PluginsAutoConfiguration::class.java
        ))
    }

    test("supports no configuration") {
      run { ctx: AssertableApplicationContext ->
        expect {
          that(ctx.getBean("pluginManager")).isA<SpinnakerPluginManager>()
          that(ctx.getBean("pluginBeanPostProcessor")).isA<ExtensionBeanDefinitionRegistryPostProcessor>()
        }
      }
    }
  }
}
