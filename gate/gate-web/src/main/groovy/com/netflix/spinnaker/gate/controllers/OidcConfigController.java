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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.OidcConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class OidcConfigController {
  @Autowired
  OidcConfigService oidcConfigService;

  @RequestMapping(value = "/oidcConfigs", method = RequestMethod.GET)
  List byApp(@RequestParam(value = "app") String app) {
    return oidcConfigService.getOidcConfigs(app);
  }

  @RequestMapping(value = "/oidcConfig", method = RequestMethod.GET)
  Map byId(@RequestParam(value = "id") String id) {
    return oidcConfigService.getOidcConfig(id);
  }
}
