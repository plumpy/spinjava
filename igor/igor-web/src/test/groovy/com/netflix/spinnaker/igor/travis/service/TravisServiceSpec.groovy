/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.service

import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.Result
import com.netflix.spinnaker.igor.travis.client.TravisClient
import com.netflix.spinnaker.igor.travis.client.model.Build
import spock.lang.Shared
import spock.lang.Specification


class TravisServiceSpec extends Specification{
    @Shared
    TravisClient client

    @Shared
    TravisService service

    void setup() {
        client = Mock(TravisClient)
        service = new TravisService('travis-ci', 'http://my.travis.ci', 'someToken', client)
    }

    def "getGenericBuild(build, repoSlug)" () {
        given:
        Build build = Mock(Build)

        when:
        GenericBuild genericBuild = service.getGenericBuild(build, "some/repo-slug")

        then:
        genericBuild.number == 1337
        genericBuild.result == Result.SUCCESS
        genericBuild.duration == 32
        genericBuild.timestamp == "1458051084000"

        1 * build.number >> 1337
        2 * build.state >> 'passed'
        1 * build.duration >> 32
        1 * build.finishedAt >> new Date()
        1 * build.timestamp() >> 1458051084000
    }
}
