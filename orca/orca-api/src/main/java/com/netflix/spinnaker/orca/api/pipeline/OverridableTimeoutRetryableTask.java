/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.orca.api.pipeline;

import com.netflix.spinnaker.kork.annotations.Alpha;

/**
 * A retryable task whose timeout is taken from the top level stage if that value has been
 * overridden.
 *
 * <p>These are typically wait/monitor stages
 *
 * <p>TODO(rz): What even is the point of this interface?
 */
@Alpha
public interface OverridableTimeoutRetryableTask extends RetryableTask {}
