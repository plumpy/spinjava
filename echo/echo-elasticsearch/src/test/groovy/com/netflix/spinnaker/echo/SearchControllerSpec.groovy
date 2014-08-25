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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Ensures the correct parameters are passed to the Search index
 */
class SearchControllerSpec extends Specification {

    MockMvc mockMvc
    @Subject
    SearchController controller

    static final String QUERY = '''{
        "match" : {
            "message" : "this is a test"
        }
    }'''

    void setup() {
        controller = new SearchController()
        controller.searchIndex = Mock(SearchIndex)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    void 'get'() {
        when:
        ResultActions resultActions = mockMvc.perform(get('/search/get/igor/build/499-5553'))

        then:
        1 * controller.searchIndex.get('igor', 'build', '499-5553')
        resultActions.andExpect MockMvcResultMatchers.status().isOk()
    }

    @Unroll
    void 'search events'() {
        when:
        ResultActions resultActions = mockMvc.perform(get(queryString))

        then:
        1 * controller.searchIndex.searchEvents(start, end, source, type, full)
        resultActions.andExpect MockMvcResultMatchers.status().isOk()

        where:
        queryString                                                    | end  | source | type    | full
        '/search/events/12345'                                         | null | null   | null    | false
        '/search/events/12345?end=25'                                  | '25' | null   | null    | false
        '/search/events/12345?type=build&source=igor'                  | null | 'igor' | 'build' | false
        '/search/events/12345?full=true'                               | null | null   | null    | true
        '/search/events/12345?type=build&source=igor&end=25&full=true' | '25' | 'igor' | 'build' | true
        start = '12345'
    }

    void 'direct search metadata'() {
        when:
        ResultActions resultActions = mockMvc.perform(
            post('/search/es/metadata')
                .contentType(MediaType.APPLICATION_JSON)
                .content(QUERY)
        )

        then:
        1 * controller.searchIndex.directSearchMetadata(QUERY)
        resultActions.andExpect MockMvcResultMatchers.status().isOk()
    }

    void 'direct search keys'() {
        when:
        ResultActions resultActions = mockMvc.perform(
            post('/search/es/igor/build')
                .contentType(MediaType.APPLICATION_JSON)
                .content(QUERY)
        )

        then:
        1 * controller.searchIndex.directSearch('igor', 'build', QUERY)
        resultActions.andExpect MockMvcResultMatchers.status().isOk()
    }

}
