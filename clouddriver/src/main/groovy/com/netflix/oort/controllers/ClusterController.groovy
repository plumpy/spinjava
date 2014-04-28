package com.netflix.oort.controllers

import com.netflix.frigga.Names
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate

@RestController
@RequestMapping("/deployables/{deployable}/clusters")
class ClusterController {

  RestTemplate restTemplate = new RestTemplate()

  @RequestMapping(method = RequestMethod.GET)
  def list(@PathVariable("deployable") String deployable) {
    getMultiRegionClusterList(deployable)
  }

  @RequestMapping(value = "/{cluster}", method = RequestMethod.GET)
  def get(@PathVariable("deployable") String deployable, @PathVariable("cluster") String cluster) {
    getMultiRegionClusterList(deployable, cluster)
  }

  Map<String, List<String>> getMultiRegionClusterList(String deployable, String clusterName=null) {
    def clusters = [:]
    ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].each { String region ->
      def regionClusters = getClustersForRegion(deployable, region, clusterName)
      regionClusters.each { String cluster, List<String> serverGroups ->
        if (!clusters.containsKey(cluster)) {
          clusters[cluster] = [:]
        }
        clusters[cluster][region] = serverGroups
      }
    }
    clusters
  }

  Map<String, List<String>> getClustersForRegion(String deployable, String region, String cluster=null) {
    List<String> asgs = restTemplate.getForEntity("http://entrypoints-v2.${region}.test.netflix.net:7001/REST/v2/aws/autoScalingGroups", List).body
    def thisDeployablesAsgs = asgs.findAll {
      def names = Names.parseName it
      def result = names.app == deployable
      if (cluster) {
        result && names.cluster == cluster
      } else {
        result
      }
    }
    thisDeployablesAsgs.inject([:]) { Map m, String asg ->
      def names = Names.parseName asg
      if (!m.containsKey(names.cluster)) {
        m[names.cluster] = []
      }
      m[names.cluster] << asg
      m
    }
  }

  static class ServerGroup {
    String name
    List<Instance> instances
  }

  static class Instance {
    String name
    String publicDNS
    String publicIp
    String privateDNS
    String privateIp
    String health
  }
}
