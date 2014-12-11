/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.ImageService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RequestMapping("/images")
@RestController
class ImageController {
  @Autowired
  ImageService imageService

  @RequestMapping(value = "/{account}/{region}/{imageId}", method = RequestMethod.GET)
  List<Map> getImageDetails(@PathVariable(value = "account") String account,
                            @PathVariable(value = "region") String region,
                            @PathVariable(value = "imageId") String imageId,
                            @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider) {
    imageService.getForAccountAndRegion(provider, account, region, imageId)
  }

  @RequestMapping(value = "/find", method = RequestMethod.GET)
  List<Map> findImages(@RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
                       @RequestParam(value = "q") String query,
                       @RequestParam(value = "region", required = false) String region) {
    imageService.search(provider, query, region)
  }
}
