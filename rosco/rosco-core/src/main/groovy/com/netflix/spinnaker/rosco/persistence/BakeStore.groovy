/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.persistence

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus

/**
 * Persistence service for in-flight and completed bakes.
 */
interface BakeStore {

  /**
   * Store the region, bakeRequest and bakeStatus in association with both the bakeKey and bakeStatus.id.
   * None of the arguments may be null.
   */
  public void storeBakeStatus(String bakeKey, String region, BakeRequest bakeRequest, BakeStatus bakeStatus)

  /**
   * Update the completed bake details associated with both the bakeKey and bakeDetails.id. bakeDetails may not be null.
   */
  public void updateBakeDetails(Bake bakeDetails)

  /**
   * Update the bakeStatus associated with both the bakeKey and bakeStatus.id. If bakeStatus.state is neither
   * pending nor running, remove bakeStatus.id from the set of incomplete bakes. bakeStatus may not be null.
   */
  public void updateBakeStatus(BakeStatus bakeStatus)

  /**
   * Update the bakeStatus and bakeLogs associated with both the bakeKey and bakeStatus.id. If bakeStatus.state is
   * neither pending nor running, remove bakeStatus.id from the set of incomplete bakes. bakeStatus may not be null.
   */
  public void updateBakeStatus(BakeStatus bakeStatus, Map<String, String> logsContent)

  /**
   * Store the error in association with both the bakeId and the bakeKey. Neither argument may be null.
   */
  public void storeBakeError(String bakeId, String error)

  /**
   * Retrieve the region specified with the original request that is associated with the bakeId. bakeId may be null.
   */
  public String retrieveRegionById(String bakeId)

  /**
   * Retrieve the bake status associated with the bakeKey. bakeKey may be null.
   */
  public BakeStatus retrieveBakeStatusByKey(String bakeKey)

  /**
   * Retrieve the bake status associated with the bakeId. bakeId may be null.
   */
  public BakeStatus retrieveBakeStatusById(String bakeId)

  /**
   * Retrieve the completed bake details associated with the bakeId. bakeId may be null.
   */
  public Bake retrieveBakeDetailsById(String bakeId)

  /**
   * Retrieve the logs associated with the bakeId. bakeId may be null.
   */
  public Map<String, String> retrieveBakeLogsById(String bakeId)

  /**
   * Delete the bake status, completed bake details and logs associated with the bakeKey. If the bake is still
   * incomplete, remove the bake id from the set of incomplete bakes.
   */
  public boolean deleteBakeByKey(String bakeKey)

  /**
   * Cancel the incomplete bake associated with the bake id. Delete the bake status, completed bake details and logs
   * associated with the bake id. If the bake is still incomplete, remove the bake id from the set of incomplete bakes.
   */
  public boolean cancelBakeById(String bakeId)

  /**
   * Retrieve the set of incomplete bake ids.
   */
  public Set<String> getIncompleteBakeIds()

}
