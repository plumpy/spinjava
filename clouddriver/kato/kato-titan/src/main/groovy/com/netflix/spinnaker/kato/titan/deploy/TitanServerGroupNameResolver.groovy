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

package com.netflix.spinnaker.kato.titan.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.kato.helpers.AbstractServerGroupNameResolver
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.model.Job

/**
 * @author sthadeshwar
 */
class TitanServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private final TitanClient titanClient

  TitanServerGroupNameResolver(TitanClient titanClient) {
    this.titanClient = titanClient
  }

  @Override
  String getPreviousServerGroupName(String clusterName) {
    def clusterNameParts = Names.parseName(clusterName)
    List<Job> jobs = titanClient.getJobsByApplication(clusterNameParts.app)
                                .findAll { it.name?.startsWith(clusterName) }
    if (jobs) {
      jobs.sort(true, {it.submittedAt}).reverse(true)
      return jobs.get(0).name
    }
    return null
  }
}
