/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.security.rolesprovider.github.client


import retrofit.client.Response
import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query

/**
 * Interface for interacting with a GitHub REST API
 */
interface GitHubClient {

  @GET('/orgs/{org}/members/{username}')
  Response isMemberOfOrganization(
    @Path('org') String org,
    @Path('username') String username)

   // This one should use the Current User credentials
  @GET('/user/teams')
  List<GitHubMaster.Team> getUserTeams()

  @GET('/orgs/{org}/teams')
  List<GitHubMaster.Team> getOrgTeams(
    @Path('org') String org,
    @Query('per_page') int paginationValue
  )

  @GET('/teams/{idTeam}/memberships/{username}')
  GitHubMaster.TeamMembership isMemberOfTeam(
    @Path('idTeam') Long idTeam,
    @Path('username') String username
  )
}

