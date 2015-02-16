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

package com.netflix.spinnaker.gate.config

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.gate.retrofit.EurekaOkClient
import com.netflix.spinnaker.gate.retrofit.Slf4jRetrofitLogger
import com.netflix.spinnaker.gate.services.EurekaLookupService
import com.netflix.spinnaker.gate.services.internal.*
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.metrics.repository.MetricRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.session.data.redis.config.annotation.web.http.GateRedisHttpSessionConfiguration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.converter.JacksonConverter
import retrofit.http.Path
import retrofit.http.Query

import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static retrofit.Endpoints.newFixedEndpoint

@CompileStatic
@Configuration
@Import(GateRedisHttpSessionConfiguration)
class GateConfig {

  public static final String AUTHENTICATION_REDIRECT_HEADER_NAME = "X-AUTH-REDIRECT-URL"

  @Bean
  JedisConnectionFactory jedisConnectionFactory(
      @Value('${redis.connection:redis://localhost:6379}') String connection) {
    URI redis = URI.create(connection)
    def factory = new JedisConnectionFactory()
    factory.hostName = redis.host
    factory.port = redis.port
    if (redis.userInfo) {
      factory.password = redis.userInfo.split(":", 2)[1]
    }
    factory
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate)
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Bean
  ExecutorService executorService() {
    Executors.newCachedThreadPool()
  }

  @Autowired
  ExtendedRegistry extendedRegistry

  @Autowired
  EurekaLookupService eurekaLookupService

  @Autowired
  ServiceConfiguration serviceConfiguration

  @Bean
  OortService oortDeployService() {
    createClient "oort", OortService
  }

  @Bean
  OrcaService orcaService() {
    createClient "orca", OrcaService
  }

  @Bean
  Front50Service front50Service() {
    createClient "front50", Front50Service
  }

  @Bean
  MortService mortService() {
    createClient "mort", MortService
  }

  @Bean
  KatoService katoService() {
    createClient "kato", KatoService
  }

  //---- optional backend components:
  @Bean
  @ConditionalOnProperty('services.echo.enabled')
  EchoService echoService() {
    createClient "echo", EchoService
  }

  @Bean
  @ConditionalOnProperty('services.flapjack.enabled')
  FlapJackService flapJackService() {
    createClient "flapjack", FlapJackService
  }

  @Bean
  @ConditionalOnProperty('services.mayo.enabled')
  MayoService mayoService() {
    createClient "mayo", MayoService
  }

  @Bean
  @ConditionalOnProperty('services.igor.enabled')
  IgorService igorService() {
    createClient "igor", IgorService
  }

  @Bean
  @ConditionalOnProperty('services.flex.enabled')
  FlexService flexService() {
    createClient "flex", FlexService
  }

  @Bean
  @ConditionalOnMissingBean(FlexService)
  FlexService noopFlexService(ServiceConfiguration serviceConfiguration, MetricRepository metricRepository,
                              EurekaLookupService eurekaLookupService) {
    return new FlexService() {
      @Override
      List<Map> getForCluster(@Path("application") String application,
                              @Path("account") String account,
                              @Path("cluster") String cluster) {
        return []
      }

      @Override
      List<Map> getForAccount(@Path("account") String account) {
        return []
      }

      @Override
      List<Map> getForAccountAndRegion(@Path("account") String account, @Query("region") String region) {
        return []
      }
    }
  }

  private <T> T createClient(String serviceName, Class<T> type) {
    Service service = serviceConfiguration.getService(serviceName)
    if (service == null) {
      throw new IllegalArgumentException("Unknown service ${serviceName} requested of type ${type}")
    }
    if (!service.enabled) {
      return null
    }
    Endpoint endpoint = serviceConfiguration.discoveryHosts && service.vipAddress ?
        newFixedEndpoint("niws://${service.vipAddress}")
        : newFixedEndpoint(service.baseUrl)

    def client = new EurekaOkClient(extendedRegistry, serviceName, eurekaLookupService)

    new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setClient(client)
        .setConverter(new JacksonConverter())
        .setLogLevel(RestAdapter.LogLevel.BASIC)
        .setLog(new Slf4jRetrofitLogger(type))
        .build()
        .create(type)
  }

  @Bean
  Filter simpleCORSFilter() {
    new Filter() {
      public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
          throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;
        String origin = request.getHeader("Origin") ?: "*"
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type");
        response.setHeader("Access-Control-Expose-Headers", [AUTHENTICATION_REDIRECT_HEADER_NAME].join(", "))
        chain.doFilter(req, res);
      }

      public void init(FilterConfig filterConfig) {}

      public void destroy() {}
    }
  }

  @Component
  static class HystrixFilter implements Filter {
    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
      HystrixRequestContext.initializeContext()
      chain.doFilter(request, response)
    }

    void init(FilterConfig filterConfig) throws ServletException {}

    void destroy() {}
  }
}
