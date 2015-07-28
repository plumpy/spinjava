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

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.BuildContent
import com.netflix.spinnaker.igor.history.model.BuildDetails
import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import rx.Observable
import rx.Scheduler.Worker
import rx.functions.Action0
import rx.schedulers.Schedulers

import javax.annotation.PreDestroy
import java.util.concurrent.TimeUnit

/**
 * Monitors new jenkins builds
 */
@Slf4j
@Service
@SuppressWarnings('CatchException')
class BuildMonitor implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    Environment environment

    Worker worker = Schedulers.io().createWorker()

    @Autowired
    JenkinsCache cache

    @Autowired(required = false)
    EchoService echoService

    @Autowired
    JenkinsMasters jenkinsMasters

    def lastPoll

    @SuppressWarnings('GStringExpressionWithinString')
    @Value('${spinnaker.build.pollInterval:60}')
    int pollInterval

    static final String BUILD_IN_PROGRESS = "BUILDING"

    @Autowired(required = false)
    DiscoveryClient discoveryClient

    boolean isInService() {
        if (discoveryClient == null) {
            log.info("no DiscoveryClient, assuming InService")
            true
        } else {
            def remoteStatus = discoveryClient.instanceRemoteStatus
            log.info("current remote status ${remoteStatus}")
            remoteStatus == InstanceInfo.InstanceStatus.UP
        }
    }

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        log.info('Started')
        worker.schedulePeriodically(
            {
                if (isInService()) {
                    jenkinsMasters.map.keySet().each { master ->
                        changedBuilds(master)
                    }
                } else {
                    log.info("not in service")
                }
            } as Action0, 0, pollInterval, TimeUnit.SECONDS
        )
    }

    @PreDestroy
    void stop() {
        log.info('Stopped')
        if (!worker.isUnsubscribed()) {
            worker.unsubscribe()
        }
    }

    /*
     * retrieves a list of new builds that are different than the ones in cache and keeps track of the builds it has
     */

    List<Map> changedBuilds(String master) {

        log.info('Checking for new builds for ' + master)
        List<Map> results = []

        try {
            lastPoll = System.currentTimeMillis()
            List<String> cachedBuilds = cache.getJobNames(master)
            List<Project> builds = jenkinsMasters.map[master].projects?.list
            List<String> buildNames = builds*.name
            Observable.from(cachedBuilds).filter { String name ->
                !(name in buildNames)
            }.subscribe(
                { String jobName ->
                    log.info "Removing ${master}:${jobName}"
                    cache.remove(master, jobName)
                }, {
                log.error("Error: ${it.message}")
            }, {} as Action0
            )

            Observable.from(builds).subscribe(
                { Project project ->
                    boolean addToCache = false
                    Map cachedBuild = null
                    log.debug "processing build : ${project?.name} : building? ${project?.lastBuild?.building}"
                    if (!project?.lastBuild) {
                        log.debug "no builds found for ${project.name}, skipping"
                    } else if (cachedBuilds.contains(project.name)) {
                        cachedBuild = cache.getLastBuild(master, project.name)
                        if ((project.lastBuild.building != cachedBuild.lastBuildBuilding) ||
                            (project.lastBuild.number != Integer.valueOf(cachedBuild.lastBuildLabel))) {
                            log.info "Build changed: ${master}: ${project.name} : ${project.lastBuild.number} : ${project.lastBuild.building}"
                            if (echoService && cachedBuild.lastBuildBuilding && (project.lastBuild.number != Integer.valueOf(cachedBuild.lastBuildLabel))) {
                                // we cached a build in progress, but missed the build result (a new build is underway or complete)
                                // fetch the final old build status and post the final result to echo
                                log.info "fetching missed completed build info for ${cachedBuild.lastBuildLabel}"
                                try {
                                    Build finishedBuild = jenkinsMasters.map[master].getBuild(project.name, Integer.valueOf(cachedBuild.lastBuildLabel))
                                    Project oldProject = new Project(name: project.name, lastBuild: finishedBuild)
                                    echoService.postBuild(
                                        new BuildDetails(content: new BuildContent(project: oldProject, master: master)))
                                    // don't add to cache since we already have a newer build to cache
                                } catch (e) {
                                    log.error("An error occurred fetching ${master}:${project.name}:${cachedBuild.lastBuildLabel}", e)
                                }
                            }
                            addToCache = true
                        }
                    } else {
                        log.info "New Build: ${master}: ${project.name} : ${project.lastBuild.number} : " +
                            "${project.lastBuild.result}"
                        addToCache = true
                    }
                    if (addToCache) {
                        project.lastBuild.result = project?.lastBuild?.result ?: project.lastBuild.building ? BUILD_IN_PROGRESS : ""
                        log.debug "setting result to ${project.lastBuild.result}"
                        cache.setLastBuild(master, project.name, project.lastBuild.number, project.lastBuild.building)
                        if (echoService) {
                            echoService.postBuild(
                                new BuildDetails(content: new BuildContent(project: project, master: master))
                            )
                        }
                        results << [previous: cachedBuild, current: project]
                    }
                }, {
                log.error("Error: ${it.message}")
            }, {
            } as Action0
            )
        } catch (e) {
            log.error("failed to update master $master", e)
        }
        results
    }
}
