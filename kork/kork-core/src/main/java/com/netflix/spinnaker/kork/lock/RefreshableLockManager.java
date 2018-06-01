/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.kork.lock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock Manager with heartbeat support.
 */
public interface RefreshableLockManager extends LockManager {
  HeartbeatResponse heartbeat(final HeartbeatLockRequest heartbeatLockRequest);
  void queueHeartbeat(final HeartbeatLockRequest heartbeatLockRequest);

  class HeartbeatLockRequest {
    private AtomicReference<Lock> lock;
    private final Duration heartbeatDuration;
    private final Instant startedAt;
    private final Clock clock;

    public HeartbeatLockRequest(Lock lock, Clock clock, Duration heartbeatDuration) {
      this.lock = new AtomicReference<>(lock);
      this.clock = clock;
      this.startedAt = clock.instant();
      this.heartbeatDuration = heartbeatDuration;
    }

    public Lock getLock() {
      return lock.get();
    }

    public void setLock(final Lock lock) {
      this.lock.set(lock);
    }

    public Duration getHeartbeatDuration() {
      return heartbeatDuration;
    }

    public Instant getStartedAt() {
      return startedAt;
    }

    public boolean timesUp() {
      return Duration.between(startedAt, clock.instant()).compareTo(heartbeatDuration) >= 0;
    }

    public Duration getRemainingLockDuration() {
      return Duration.between(startedAt, clock.instant()).minus(heartbeatDuration).abs();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      HeartbeatLockRequest that = (HeartbeatLockRequest) o;
      return Objects.equals(lock, that.lock) &&
        Objects.equals(heartbeatDuration, that.heartbeatDuration) &&
        Objects.equals(startedAt, that.startedAt) &&
        Objects.equals(clock, that.clock);
    }

    @Override
    public int hashCode() {
      return Objects.hash(lock, heartbeatDuration, startedAt, clock);
    }
  }

  class HeartbeatResponse {
    private final Lock lock;
    private final LockStatus lockStatus;

    public HeartbeatResponse(Lock lock, LockStatus lockStatus) {
      this.lock = lock;
      this.lockStatus = lockStatus;
    }

    public Lock getLock() {
      return lock;
    }

    public LockStatus getLockStatus() {
      return lockStatus;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      HeartbeatResponse that = (HeartbeatResponse) o;
      return Objects.equals(lock, that.lock) &&
        lockStatus == that.lockStatus;
    }

    @Override
    public int hashCode() {
      return Objects.hash(lock, lockStatus);
    }
  }

  class LockFailedHeartbeatException extends LockException {
    public LockFailedHeartbeatException(String message) {
      super(message);
    }

    public LockFailedHeartbeatException(String message, Throwable cause) {
      super(message, cause);
    }

    public LockFailedHeartbeatException(Throwable cause) {
      super(cause);
    }
  }
}
