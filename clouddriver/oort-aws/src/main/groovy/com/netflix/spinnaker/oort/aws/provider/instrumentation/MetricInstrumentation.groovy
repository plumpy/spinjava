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

package com.netflix.spinnaker.oort.aws.provider.instrumentation

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.Id
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit

@Component
class MetricInstrumentation implements ExecutionInstrumentation {

  private final Logger logger = LoggerFactory.getLogger(MetricInstrumentation)
  private static final ThreadLocal<ConcurrentMap<String, Long>> timingsMap = new InheritableThreadLocal<ConcurrentMap<String, Long>>() {
    @Override
    protected ConcurrentMap<String, Long> initialValue() {
      new ConcurrentHashMap<String, Long>();
    }
  }

  private final ExtendedRegistry extendedRegistry

  private final Id timingId
  private final Id counterId

  @Autowired
  public MetricInstrumentation(ExtendedRegistry extendedRegistry) {
    this.extendedRegistry = extendedRegistry
    timingId = extendedRegistry.createId('executionTime').withTag('className', MetricInstrumentation.simpleName)
    counterId = extendedRegistry.createId('executionCount').withTag('className', MetricInstrumentation.simpleName)
  }


  private static String agentName(CachingAgent agent) {
    "$agent.providerName/$agent.agentType"
  }

  @Override
  void executionStarted(CachingAgent agent) {
    Long previous = timingsMap.get().put(agentName(agent), System.nanoTime())
    if (previous != null) {
      logger.warn("Metric value not cleared for ${agentName(agent)}")
    }
  }

  @Override
  void executionCompleted(CachingAgent agent) {
    Long startTime = timingsMap.get().remove(agentName(agent))
    if (startTime != null) {
      extendedRegistry.timer(timingId.withTag('agent', agentName(agent))).record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
    }
    extendedRegistry.counter(counterId.withTag('agent', agentName(agent)).withTag('status', 'success')).increment()
  }

  @Override
  void executionFailed(CachingAgent agent, Throwable cause) {
    timingsMap.get().remove(agentName(agent))
    extendedRegistry.counter(counterId.withTag('agent', agentName(agent)).withTag('status', 'failure')).increment()
  }
}
