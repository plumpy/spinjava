/*
 * Copyright 2018 xiaohongshu, Inc.
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

package com.netflix.spinnaker.echo.bearychat

import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.client.Response
import retrofit.http.Query

interface BearychatService {

  @GET("/v1/user.list")
  List<BearychatUserInfo> getUserList(@Query("token") String token)

  @POST("/v1/p2p.create")
  CreateP2PChannelResponse createp2pchannel(@Query("token") String token,
                                            @Body CreateP2PChannelPara para)

  @POST("/v1/message.create")
  Response sendMessage(@Query("token") String token,
                       @Body SendMessagePara para)
}
