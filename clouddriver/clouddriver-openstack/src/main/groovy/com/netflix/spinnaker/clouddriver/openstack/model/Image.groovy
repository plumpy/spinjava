/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.model

/**
 * Image interface to be promoted to core.
 * //TODO - promote to core.
 */
interface Image {
  /**
   * Returns an image identifier.
   * @return
   */
  String getId()

  /**
   * Returns an image name.
   * @return
   */
  String getName()

  /**
   * Returns an images status.
   * @return
   */
  String getStatus()

  /**
   * Contains images properties used to create an image.
   */
  Map<String, Object> properties
}
