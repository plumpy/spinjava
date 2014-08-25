/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Enables event search
 */
@RestController
class SearchController {

    @Autowired
    SearchIndex searchIndex

    @RequestMapping(value = '/search/get/{source}/{type}/{id}', method = RequestMethod.GET)
    Map get(
        @PathVariable(value = 'source') String source,
        @PathVariable(value = 'type') String type,
        @PathVariable(value = 'id') String id) {
        searchIndex.get(source, type, id)
    }

    @RequestMapping(value = '/search/events/{start}', method = RequestMethod.GET)
    Map eventsByDate(
        @PathVariable(value = 'start') String start,
        @RequestParam(value = 'source', required = false) String source,
        @RequestParam(value = 'type', required = false) String type,
        @RequestParam(value = 'end', required = false) String end,
        @RequestParam(value = 'full', required = false) String full
    ) {
        boolean isFull = Boolean.parseBoolean(full)
        searchIndex.searchEvents(start, end, source, type, isFull)
    }

    @RequestMapping(value = '/search/es/{source}/{type}', method = RequestMethod.POST)
    String directSearch(
        @PathVariable(value = 'source') String source,
        @PathVariable(value = 'type') String type,
        @RequestBody String query) {
        searchIndex.directSearch(source, type, query)
    }

    @RequestMapping(value = '/search/es/metadata', method = RequestMethod.POST)
    String directSearchMetadata(@RequestBody String query) {
        searchIndex.directSearchMetadata(query)
    }

}
