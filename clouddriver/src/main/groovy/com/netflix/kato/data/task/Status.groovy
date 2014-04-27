package com.netflix.kato.data.task

/**
 * This interface is used to represent the status of a Task for a point in time. Often should be backed by a POGO, but
 * may be used for more complex requirements, like querying a database or centralized task system in a multi-threaded/
 * multi-service environment.
 *
 * A psuedo-composite key of a Status is its phase and status strings.
 *
 * @author Dan Woods
 */
public interface Status {
  /**
   * Returns the current phase of the execution. This is useful for representing different parts of a Task execution, and
   * a "status" String will be tied
   */
  String getPhase()

  /**
   * Returns the current status of the Task in its given phase.
   */
  String getStatus()

  /**
   * Informs completion of the task.
   */
  Boolean isCompleted()

  /**
   * Informs whether the task has failed or not. A "failed" state is always indicitive of a "completed" state.
   */
  Boolean isFailed()
}