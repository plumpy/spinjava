/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.event.persistence

import com.netflix.spinnaker.clouddriver.event.Aggregate
import com.netflix.spinnaker.clouddriver.event.SpinnakerEvent

/**
 * The [EventRepository] is responsible for reading and writing immutable event logs from a persistent store.
 *
 * There's deliberately no eviction API. It's expected that each [EventRepository] implementation will implement
 * that functionality on their own, including invocation apis and/or scheduling; tailoring to the operational
 * needs of that backend.
 */
interface EventRepository {
  /**
   * Save [events] to an [Aggregate].
   *
   * @param aggregateType The aggregate collection name
   * @param aggregateId The unique identifier of the event aggregate within the [aggregateType]
   * @param originatingVersion The aggregate version that originated the [events]. This is used to ensure events are
   *                           added only based off the latest aggregate state
   * @param newEvents A list of events to be saved
   */
  fun save(aggregateType: String, aggregateId: String, originatingVersion: Long, newEvents: List<SpinnakerEvent>)

  /**
   * List all events for a given [Aggregate].
   *
   * @param aggregateType The aggregate collection name
   * @param aggregateId The unique identifier of the event aggregate within the [aggregateType]
   * @return An ordered list of events, oldest to newest
   */
  fun list(aggregateType: String, aggregateId: String): List<SpinnakerEvent>

  /**
   * List all aggregates for a given type.
   *
   * @param aggregateType The aggregate collection name. If not provided, all aggregates will be returned.
   * @return An unordered list of matching aggregates.
   */
  fun listAggregates(aggregateType: String?): List<Aggregate>
}
