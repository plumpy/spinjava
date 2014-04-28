package com.netflix.oort.controllers

import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.model.SelectRequest
import com.netflix.frigga.Names
import groovy.util.logging.Log4j
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate

@RestController
@RequestMapping("/deployables")
class DeployableController {
  @RequestMapping(method = RequestMethod.GET)
  def list() {
    def cache = Cacher.get()
    cache.inject([:]) { Map map, String deployable, Map v ->
      if (!map.containsKey(deployable)) {
        map[deployable] = [instanceCount:0, asgCount:0, attributes: v.attributes]
      }
      v.clusters.each { String clusterName, Map clusterRegions ->
        clusterRegions.each { String regionName, List<Map> asg ->
          map[deployable].asgCount++
          map[deployable].instanceCount += asg.instances.size()
        }
      }
      map
    }
  }

  @Component
  @Log4j
  static class Cacher {
    private static def firstRun = true
    private static def restTemplate = new RestTemplate()
    private static def lock = new ReentrantLock()
    private static def map = new ConcurrentHashMap()
    private static def executorService = Executors.newFixedThreadPool(20)

    @Autowired
    AmazonSimpleDB amazonSimpleDB

    @Value('${Aws.SimpleDB.Name:RESOURCE_REGISTRY}')
    String database

    static Map get() {
      lock.lock()
      def m = map
      lock.unlock()
      m
    }

    @Scheduled(fixedRate = 30000l)
    void cacheClusters() {
      def request = new SelectRequest("select * from $database limit 2500")
      def result = amazonSimpleDB.select(request)
      def simpleDb = [:]
      while(true) {
        simpleDb += result.items.collectEntries {
          [(it.name.toLowerCase()): it.attributes.collectEntries {[(it.name):it.value]}]
        }
        if (result.nextToken) {
          result = amazonSimpleDB.select(request.withNextToken(result.nextToken))
        } else {
          break
        }
      }

      if (firstRun) {
        lock.lock()
      }
      def run = new ConcurrentHashMap()
      def stopwatch = new StopWatch()
      stopwatch.start()
      log.info "Beginning caching."
      def c = { String region ->
        List<String> asgs = restTemplate.getForEntity("http://entrypoints-v2.${region}.test.netflix.net:7001/REST/v2/aws/autoScalingGroups", List).body

        def c = { String asgName ->
          def names = Names.parseName asgName
          def asg = restTemplate.getForEntity("http://entrypoints-v2.${region}.test.netflix.net:7001/REST/v2/aws/autoScalingGroups/$asgName", Map).body

          if (!run.containsKey(names.app)) {
            run[names.app] = new ConcurrentHashMap()
          }
          if (!run[names.app].containsKey("attributes") && simpleDb.containsKey(names.app.toLowerCase())) {
            run[names.app]["attributes"] = simpleDb[names.app.toLowerCase()]
          }
          if (!run[names.app].containsKey("clusters")) {
            run[names.app]["clusters"] = new ConcurrentHashMap()
          }
          if (!run[names.app]["clusters"].containsKey(names.cluster)) {
            run[names.app]["clusters"][names.cluster] = new ConcurrentHashMap()
          }
          if (!run[names.app]["clusters"][names.cluster].containsKey(region)) {
            run[names.app]["clusters"][names.cluster][region] = []
          }
          run[names.app]["clusters"][names.cluster][region] << asg
        }

        def callables = asgs.collect { c.curry(it) }
        executorService.invokeAll(callables as List<Callable>)
      }
      def callables = ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].collect { c.curry(it) }
      executorService.invokeAll(callables)*.get()
      if (!lock.isLocked()) {
        lock.lock()
      }
      map = run.sort { a, b -> a.key.toLowerCase() <=> b.key.toLowerCase() }
      lock.unlock()
      if (firstRun) {
        firstRun = false
      }
      stopwatch.stop()
      log.info "Done caching in ${stopwatch.shortSummary()}"
    }
  }

}
