package com.netflix.kato.data.task

/**
 * This interface represents the state of a given execution. Implementations must allow for updating and completing/failing
 * status, as well as providing the start time of the task.
 *
 * @author Dan Woods
 */
public interface Task {
  /**
   * A unique identifier for the task, which can be used to retrieve it at a later time.
   */
  String getId()

  /**
   * A comprehensive history of this task's execution.
   */
  List<Status> getHistory()

  /**
   * This method is used to update the status of the Task with given phase and status strings.
   * @param phase
   * @param status
   */
  void updateStatus(String phase, String status)

  /**
   * This method will complete the task and will represent completed = true from the Task's {@link #getStatus()} method.
   */
  void complete()

  /**
   * This method will fail the task and will represent completed = true and failed = true from the Task's
   * {@link #getStatus()} method.
   */
  void fail()

  /**
   * This method will return the current status of the task.
   * @see Status
   */
  Status getStatus()

  /**
   * This returns the start time of the Task's execution in milliseconds since epoch form.
   */
  long getStartTimeMs()
}