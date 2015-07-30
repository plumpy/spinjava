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
import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.gate.retrofit.EurekaOkClient
import com.netflix.spinnaker.gate.retrofit.Slf4jRetrofitLogger
import com.netflix.spinnaker.gate.services.EurekaLookupService
import com.netflix.spinnaker.gate.services.internal.*
import com.squareup.okhttp.CipherSuite
import com.squareup.okhttp.ConnectionSpec
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.TlsVersion
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.metrics.repository.MetricRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.embedded.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.session.data.redis.config.annotation.web.http.GateRedisHttpSessionConfiguration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import retrofit.Endpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.converter.JacksonConverter
import retrofit.http.Body
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
@Slf4j
@Import(GateRedisHttpSessionConfiguration)
class GateConfig {

  public static final String AUTHENTICATION_REDIRECT_HEADER_NAME = "X-AUTH-REDIRECT-URL"

  @Value('${retrofit.logLevel:BASIC}')
  String retrofitLogLevel

  @Autowired
  RequestInterceptor spinnakerRequestInterceptor

  @Bean
  JedisConnectionFactory jedisConnectionFactory(
    @Value('${redis.connection:redis://localhost:6379}') String connection
  ) {
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

  @Autowired
  OkHttpClientConfiguration okHttpClientConfig

  @Bean OkHttpClient okHttpClient() {
    def okHttpClient = okHttpClientConfig.create()
    okHttpClient.setConnectionSpecs([
      new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_2)
        .cipherSuites(
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA
      )
        .build()
    ])

    return okHttpClient
  }

  @Bean
  OortService oortDeployService(OkHttpClient okHttpClient) {
    createClient "oort", OortService, okHttpClient
  }

  @Bean
  OrcaService orcaService(OkHttpClient okHttpClient) {
    createClient "orca", OrcaService, okHttpClient
  }

  @Bean
  Front50Service front50Service(OkHttpClient okHttpClient) {
    createClient "front50", Front50Service, okHttpClient
  }

  @Bean
  MortService mortService(OkHttpClient okHttpClient) {
    createClient "mort", MortService, okHttpClient
  }

  @Bean
  KatoService katoService(OkHttpClient okHttpClient) {
    createClient "kato", KatoService, okHttpClient
  }

  //---- optional backend components:
  @Bean
  @ConditionalOnProperty('services.echo.enabled')
  EchoService echoService(OkHttpClient okHttpClient) {
    createClient "echo", EchoService, okHttpClient
  }

  @Bean
  @ConditionalOnProperty('services.flapjack.enabled')
  FlapJackService flapJackService(OkHttpClient okHttpClient) {
    createClient "flapjack", FlapJackService, okHttpClient
  }

  @Bean
  @ConditionalOnProperty('services.igor.enabled')
  IgorService igorService(OkHttpClient okHttpClient) {
    createClient "igor", IgorService, okHttpClient
  }

  @Bean
  @ConditionalOnProperty('services.mahe.enabled')
  MaheService maheService(OkHttpClient okHttpClient) {
    createClient "mahe", MaheService, okHttpClient
  }

  @Bean
  @ConditionalOnMissingBean(MaheService)
  MaheService noopMaheService() {
    new MaheService() {
      @Override
      Map getFastPropertiesByApplication(@Path("appName") String appName) {
        return [:]
      }

      @Override
      Map getAll() {
        return [:]
      }

      @Override
      Map getByKey(@Path("key") String key) {
        return [:]
      }

      @Override
      List<String> getAllKeys() {
        return []
      }

      @Override
      Map getImpact(@Body Map scope) {
        return [:]
      }

      @Override
      Map queryScope(@Body Map scope) {
        return [:]
      }

      @Override
      Map create(@Body Map fastProperty) {
        return [:]
      }

      @Override
      String promote(@Body Map fastProperty) {
        return [:]
      }

      @Override
      Map promotionStatus(@Path("promotionId") String promotionId) {
        return [:]
      }

      @Override
      Map passPromotion(@Path("promotionId") String promotionId, @Body Boolean pass) {
        return [:]
      }

      @Override
      List promotions() {
        return []
      }

      @Override
      List promotionsByApp(@Path("appId") String appId) {
        return []
      }

      @Override
      Map delete(@Query("propId") String propId, @Query("cmcTicket") String cmcTicket, @Query("env") String env) {
        return [:]
      }
    }
  }


  @Bean
  @ConditionalOnProperty('services.flex.enabled')
  FlexService flexService(OkHttpClient okHttpClient) {
    createClient "flex", FlexService, okHttpClient
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
      List<Map> getForClusterAndRegion(@Path("application") String application,
                                       @Path("account") String account,
                                       @Path("cluster") String cluster,
                                       @Path("region") String region) {
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

  @Bean
  @ConditionalOnProperty('services.mine.enabled')
  MineService mineService(OkHttpClient okHttpClient) {
    createClient "mine", MineService, okHttpClient
  }


  @Bean
  @ConditionalOnProperty('services.scheduler.enabled')
  SchedulerService schedulerService(OkHttpClient okHttpClient) {
    createClient "scheduler", SchedulerService, okHttpClient
  }

  private <T> T createClient(String serviceName, Class<T> type, OkHttpClient okHttpClient) {
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

    def client = new EurekaOkClient(okHttpClient, extendedRegistry, serviceName, eurekaLookupService)

    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(endpoint)
      .setClient(client)
      .setConverter(new JacksonConverter())
      .setLogLevel(RestAdapter.LogLevel.valueOf(retrofitLogLevel))
      .setLog(new Slf4jRetrofitLogger(type))
      .build()
      .create(type)
  }

  @Bean
  FilterRegistrationBean simpleCORSFilter() {
    def frb = new FilterRegistrationBean(
    new Filter() {
      public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
        throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;
        String origin = request.getHeader("Origin") ?: "*"
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT, PATCH");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type");
        response.setHeader("Access-Control-Expose-Headers", [AUTHENTICATION_REDIRECT_HEADER_NAME].join(", "))
        chain.doFilter(req, res);
      }

      public void init(FilterConfig filterConfig) {}

      public void destroy() {}
    })
    frb.setOrder(Ordered.HIGHEST_PRECEDENCE)

    return frb
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  Filter authenticatedRequestFilter() {
    new AuthenticatedRequestFilter(false)
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
