/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.BuildService;
import com.netflix.spinnaker.gate.services.internal.GoogleCloudBuildTrigger;
import com.netflix.spinnaker.gate.services.internal.IgorService;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty("services.igor.enabled")
@RestController
@RequestMapping("/gcb")
public class GoogleCloudBuildController {
  private IgorService igorService;
  private BuildService buildService;

  @Autowired
  public GoogleCloudBuildController(IgorService igorService, BuildService buildService) {
    this.igorService = igorService;
    this.buildService = buildService;
  }

  @ApiOperation(value = "Retrieve the list of Google Cloud Build accounts", response = List.class)
  @GetMapping(value = "/accounts")
  List<String> getAccounts() {
    return igorService.getGoogleCloudBuildAccounts();
  }

  @ApiOperation(
      value = "Retrieve the list of Google Cloud Build triggers for a given account",
      response = List.class)
  @GetMapping(value = "/triggers/{account}")
  List<GoogleCloudBuildTrigger> getGoogleCloudBuildTriggers(
      @PathVariable("account") String account) {
    return buildService.getGoogleCloudBuildTriggersForAccount(account);
  }
}
