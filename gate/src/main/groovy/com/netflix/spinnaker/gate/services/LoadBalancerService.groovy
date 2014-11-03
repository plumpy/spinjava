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

package com.netflix.spinnaker.gate.services

import com.netflix.hystrix.*
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.Observable

@CompileStatic
@Component
class LoadBalancerService {
  private static final String SERVICE = "loadBalancers"
  private static final HystrixCommandGroupKey HYSTRIX_KEY = HystrixCommandGroupKey.Factory.asKey(SERVICE)

  @Autowired
  OortService oortService

  Observable<Map> getAll(Integer offset, Integer size, String provider = "aws") {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getAll"))) {

      @Override
      protected Observable<Map> run() {
        Observable.just(oortService.search("", "loadBalancers", provider, offset, size))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.just([:])
      }

      @Override
      protected String getCacheKey() {
        "loadBalancers-all"
      }
    }.toObservable()
  }

  Observable<Map> get(String name, String provider = "aws") {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("get"))) {

      @Override
      protected Observable<Map> run() {
        Observable.just(oortService.getLoadBalancer(provider, name))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.just([:])
      }

      @Override
      protected String getCacheKey() {
        "loadBalancers-${provider}-${name}"
      }
    }.toObservable()
  }

  Observable<List> getClusterLoadBalancers(String appName, String account, String provider, String clusterName) {
    new HystrixObservableCommand<List>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getLoadBalancersForCluster"))) {

      @Override
      protected Observable<List> run() {
        Observable.just(oortService.getClusterLoadBalancers(appName, account, clusterName, provider))
      }

      @Override
      protected Observable<List> getFallback() {
        Observable.just([])
      }

      @Override
      protected String getCacheKey() {
        "clusterloadBalancers-${provider}-${appName}-${account}-${clusterName}"
      }
    }.toObservable()
  }
}
