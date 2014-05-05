package com.netflix.kato.deploy.aws.ops.loadbalancer

import groovy.transform.Immutable

class CreateLoadBalancerResult {
  /**
   * Association of region -> loadBalancer
   */
  Map<String, LoadBalancer> loadBalancers

  @Immutable
  static class LoadBalancer {
    String name
    String dnsName
  }
}
