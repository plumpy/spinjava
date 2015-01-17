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

package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.orca.ExecutionStatus
import groovy.transform.CompileStatic

@CompileStatic
interface Stage<T extends Execution> {
  /**
   * The type as it corresponds to the Mayo configuration
   */
  String getType()

  /**
   * The name of the stage. Can be different from type, but often will be the same.
   */
  String getName()

  /**
   * Gets the execution object for this stage
   */
  T getExecution()

  /**
   * Gets the start time for this stage. May return null if the stage has not been started.
   */
  Long getStartTime()

  /**
   * Gets the end time for this stage. May return null if the stage has not yet finished.
   */
  Long getEndTime()

  /**
   * @param startTime
   */
  void setStartTime(Long startTime)

  /**
   * @param endTime
   */
  void setEndTime(Long endTime)

  /**
   * The execution status for this stage
   */
  ExecutionStatus getStatus()

  /**
   * sets the execution status for this stage
   */
  void setStatus(ExecutionStatus status)

  /**
   * Gets the last stage preceding this stage that has the specified type.
   */
  Stage preceding(String type)

  /**
   * The context driving this stage. Provides inputs necessary to component steps
   */
  Map<String, Object> getContext()

  /**
   * Returns a flag indicating if the stage is in an immutable state
   * @return
   */
  boolean isImmutable()

  /**
   * Returns the stage as an immutable object. Stage state can be discovered through {@link #isImmutable()}
   * @return
   */
  @JsonIgnore
  Stage<T> asImmutable()

  /**
   * @return a reference to the wrapped object in this event this object is the immutable wrapper
   */
  @JsonIgnore
  Stage<T> getSelf()

  /**
   * Returns the tasks that are associated with this stage. Tasks are the most granular unit of work in a stage.
   * Because tasks can be dynamically composed, this list is open updated during a stage's execution.
   *
   * @see com.netflix.spinnaker.orca.batch.StageTaskPropagationListener
   */
  List<Task> getTasks()

  /**
   * Maps the stage's context to a typed object
   */
  public <O> O mapTo(Class<O> type)

  /**
   * Maps the stage's context to a typed object at a provided pointer. Uses
   * <a href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a> notation for determining the pointer's position
   */
  public <O> O mapTo(String pointer, Class<O> type)

  /**
   * Commits a typed object back to the stage's context. The context is recreated during this operation, so callers
   * will need to re-reference the context object to have the new values reflected
   */
  public void commit(Object obj)

  /**
   * Commits a typed object back to the stage's context at a provided pointer. Uses <a href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a>
   * notation for detremining the pointer's position
   */
  void commit(String pointer, Object obj)

}
