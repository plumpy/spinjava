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


package com.netflix.spinnaker.hystrix

import com.netflix.hystrix.HystrixCommand
import com.netflix.hystrix.HystrixCommandGroupKey
import com.netflix.hystrix.HystrixCommandKey
import com.netflix.hystrix.HystrixCommandProperties
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class SimpleHystrixCommand<T> extends HystrixCommand<T> {

  private final String groupKey
  private final String commandKey

  protected final Closure work
  protected final Closure fallback

  public SimpleHystrixCommand(String groupKey,
                              String commandKey,
                              Closure work,
                              Closure fallback = null) {
    super(HystrixCommand.Setter.withGroupKey(toGroupKey(groupKey))
      .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
      .andCommandPropertiesDefaults(createHystrixCommandPropertiesSetter()))
    this.groupKey = groupKey
    this.commandKey = commandKey
    this.work = work
    this.fallback = fallback ?: { null }
  }

  @Override
  protected T run() throws Exception {
    return work()
  }

  protected T getFallback() {
    def fallbackValue = (fallback.call() as T)
    if (fallbackValue == null) {
      return super.getFallback() as T
    }

    return fallbackValue
  }

  static HystrixCommandGroupKey toGroupKey(String name) {
    HystrixCommandGroupKey.Factory.asKey(name)
  }

  @CompileDynamic
  static HystrixCommandProperties.Setter createHystrixCommandPropertiesSetter() {
    HystrixCommandProperties.invokeMethod("Setter", null)
  }
}
