package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.discovery.EurekaClient
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.DefaultAgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity
import com.netflix.spinnaker.cats.cluster.DefaultNodeStatusProvider
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.cats.sql.SqlProviderRegistry
import com.netflix.spinnaker.cats.sql.cache.SpectatorSqlCacheMetrics
import com.netflix.spinnaker.cats.sql.cache.SqlCacheMetrics
import com.netflix.spinnaker.cats.sql.cache.SqlNamedCacheFactory
import com.netflix.spinnaker.cats.sql.cluster.SqlClusteredAgentScheduler
import com.netflix.spinnaker.clouddriver.cache.CustomSchedulableAgentIntervalProvider
import com.netflix.spinnaker.clouddriver.cache.EurekaStatusNodeStatusProvider
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import org.jooq.DSLContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import java.time.Clock
import java.time.Duration
import java.util.Optional
import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnProperty("sql.cache.enabled")
@Import(DefaultSqlConfiguration::class)
@EnableConfigurationProperties(SqlAgentProperties::class)
class SqlCacheConfiguration {

  @Bean
  fun sqlCacheMetrics(registry: Registry): SqlCacheMetrics {
    return SpectatorSqlCacheMetrics(registry)
  }

  @Bean
  fun catsModule(providers: List<Provider>,
                 executionInstrumentation: List<ExecutionInstrumentation>,
                 cacheFactory: NamedCacheFactory,
                 agentScheduler: AgentScheduler<*>): CatsModule {
    return CatsModule.Builder()
      .providerRegistry(SqlProviderRegistry(providers, cacheFactory))
      .cacheFactory(cacheFactory)
      .scheduler(agentScheduler)
      .instrumentation(executionInstrumentation)
      .build(providers)
  }

  @Bean
  fun cacheFactory(jooq: DSLContext,
                   clock: Clock,
                   sqlProperties: SqlProperties,
                   cacheMetrics: SqlCacheMetrics,
                   @Value("\${sql.cache.tablePrefix:#{null}}") prefix: String?,
                   @Value("\${sql.cache.writeBatchSize:100}") writeBatchSize: Int,
                   @Value("\${sql.cache.readBatchSize:500}") readBatchSize: Int): NamedCacheFactory =
    SqlNamedCacheFactory(
      jooq,
      ObjectMapper(),
      clock,
      sqlProperties.retries,
      prefix,
      writeBatchSize,
      readBatchSize,
      cacheMetrics
    )

  @Bean
  @ConditionalOnProperty(value = ["caching.writeEnabled"], matchIfMissing = true)
  fun agentScheduler(jooq: DSLContext,
                     agentIntervalProvider: AgentIntervalProvider,
                     nodeStatusProvider: NodeStatusProvider,
                     dynamicConfigService: DynamicConfigService,
                     sqlAgentProperties: SqlAgentProperties): AgentScheduler<*> {
    return SqlClusteredAgentScheduler(
      jooq = jooq,
      nodeIdentity = DefaultNodeIdentity(),
      intervalProvider =  agentIntervalProvider,
      nodeStatusProvider =  nodeStatusProvider,
      dynamicConfigService =  dynamicConfigService,
      enabledAgentPattern = sqlAgentProperties.enabledPattern,
      agentLockAcquisitionIntervalSeconds = sqlAgentProperties.agentLockAcquisitionIntervalSeconds
    )
  }

  @Bean
  fun coreProvider(): CoreProvider = CoreProvider(emptyList())

  @Bean
  fun agentIntervalProvider(sqlAgentProperties: SqlAgentProperties): AgentIntervalProvider {
    return CustomSchedulableAgentIntervalProvider(
      Duration.ofSeconds(sqlAgentProperties.poll.intervalSeconds).toMillis(),
      Duration.ofSeconds(sqlAgentProperties.poll.errorIntervalSeconds).toMillis(),
      Duration.ofSeconds(sqlAgentProperties.poll.timeoutSeconds).toMillis()
    )
  }

  @Bean
  fun nodeStatusProvider(eurekaClient: Optional<EurekaClient>): NodeStatusProvider {
    return if (eurekaClient.isPresent) {
      EurekaStatusNodeStatusProvider(eurekaClient.get())
    } else {
      DefaultNodeStatusProvider()
    }
  }
}
