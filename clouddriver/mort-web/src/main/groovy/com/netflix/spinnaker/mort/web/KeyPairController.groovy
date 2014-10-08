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



package com.netflix.spinnaker.mort.web
import com.netflix.spinnaker.mort.model.KeyPair
import com.netflix.spinnaker.mort.model.KeyPairProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/keyPairs")
@RestController
class KeyPairController {

  @Autowired
  List<KeyPairProvider> keyPairProviders

  @RequestMapping(method = RequestMethod.GET)
  Set<KeyPair> list() {
      keyPairProviders.collectMany {
      it.all
    } as Set
  }

  // TODO: implement the rest
}
