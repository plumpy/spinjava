/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.igor.jenkins

import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import spock.lang.Specification
import spock.lang.IgnoreRest

/**
 * Tests for BuildMonitor
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'PropertyName'])
class BuildMonitorSpec extends Specification {

    JenkinsCache cache = Mock(JenkinsCache)
    JenkinsClient client = Mock(JenkinsClient)
    BuildMonitor monitor

    final MASTER = 'MASTER'

    void setup() {
        monitor = new BuildMonitor(cache: cache, jenkinsMasters: new JenkinsMasters(map: [MASTER: client]))
    }

    void 'flag a new build not found in the cache'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job1']
        1 * client.projects >> new ProjectsList(list: [new Project(name: 'job2', lastBuild : new Build(number: 1))])

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        0 * cache.getLastBuild(MASTER, 'job1')
        1 * cache.setLastBuild(MASTER, 'job2', 1, false)
        builds.size() == 1
        builds[0].current.name == 'job2'
        builds[0].current.lastBuild.number == 1
        builds[0].previous == null
    }

    void 'flag existing build with a higher number as changed'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job2']
        1 * client.projects >> new ProjectsList(list: [new Project(name: 'job2',  lastBuild : new Build(number: 5))])

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job2') >> [lastBuildLabel: 3]
        1 * cache.setLastBuild(MASTER, 'job2', 5, false)
        builds[0].current.lastBuild.number == 5
        builds[0].previous.lastBuildLabel == 3
    }

    void 'flag builds in a different state as changed'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job3']
        1 * client.projects >> new ProjectsList(
            list: [new Project(name: 'job3', lastBuild : new Build(number: 5, building: false))]
        )

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job3') >> [lastBuildLabel: 5, lastBuildRunning: true]
        1 * cache.setLastBuild(MASTER, 'job3', 5, false)
        builds[0].current.lastBuild.number == 5
        builds[0].previous.lastBuildLabel == 5
        builds[0].current.lastBuild.building == false
        builds[0].previous.lastBuildRunning == true
    }

    void 'stale builds are removed'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job3', 'job4']
        1 * client.projects >> new ProjectsList(list: [])

        when:
        monitor.changedBuilds(MASTER)

        then:
        1 * cache.remove(MASTER, 'job3')
        1 * cache.remove(MASTER, 'job4')
    }

    void 'sends an event for every intermediate build'(){
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * client.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: false))]
        )
        1 * client.getBuilds('job') >> new BuildsList(
            list: [
                new Build(number: 1),
                new Build(number: 2),
                new Build(number: 3, building: false),
                new Build(number: 4),
                new Build(number: 5),
                new Build(number: 6)
            ]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 3, lastBuildRunning: true]
        1 * cache.setLastBuild(MASTER, 'job', 6, false)
        1 * monitor.echoService.postBuild({it.content.project.lastBuild.number == 3})
        1 * monitor.echoService.postBuild({it.content.project.lastBuild.number == 4})
        1 * monitor.echoService.postBuild({it.content.project.lastBuild.number == 5})
        1 * monitor.echoService.postBuild({it.content.project.lastBuild.number == 6})
    }

    void 'emits events only for builds in list'(){
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * client.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: false))]
        )
        1 * client.getBuilds('job') >> new BuildsList(
            list: [
                new Build(number: 1),
                new Build(number: 3, building: false),
                new Build(number: 6)
            ]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 3, lastBuildRunning: true]
        1 * cache.setLastBuild(MASTER, 'job', 6, false)
        1 * monitor.echoService.postBuild({it.content.project.lastBuild.number == 3})
        1 * monitor.echoService.postBuild({it.content.project.lastBuild.number == 6})
    }

    void 'does not send event for current unchanged build'(){
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * client.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 3, building: true))]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 3, lastBuildBuilding: true]
        0 * client.getBuilds('job')
        0 * monitor.echoService.postBuild(_)
    }

    void 'does not send event for past build with already sent event'(){
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * client.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: true))]
        )
        1 * client.getBuilds('job') >> new BuildsList(
            list: [
                new Build(number: 5, building: false),
                new Build(number: 6)
            ]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 5, lastBuildBuilding: false]
        1 * cache.setLastBuild(MASTER, 'job', 6, true)
        0 * monitor.echoService.postBuild({it.content.project.lastBuild.number == 5})
        1 * monitor.echoService.postBuild({it.content.project.lastBuild.number == 6})
    }

    void 'does not send event for same build'(){
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * client.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: true))]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 6, lastBuildBuilding: true]
        0 * client.getBuilds('job')
        0 * monitor.echoService.postBuild({_})
    }


    void 'sends event for same build that has finished'(){
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * client.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: true))]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 6, lastBuildBuilding: false]
        1 * monitor.echoService.postBuild({it.content.project.lastBuild.number == 6})
    }

}
