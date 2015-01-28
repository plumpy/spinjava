package com.netflix.spinnaker.orca.notifications

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.echo.EchoEventPoller
import com.netflix.spinnaker.orca.echo.config.EchoConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.notifications.jenkins.BuildJobPipelineIndexer
import com.netflix.spinnaker.orca.notifications.jenkins.BuildJobPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.jenkins.Trigger
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import net.greghaines.jesque.Config
import net.greghaines.jesque.ConfigBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.context.ContextConfiguration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool
import rx.schedulers.Schedulers
import spock.lang.Specification

/**
 * Integration test to ensure the Jesque-based notification workflow hangs
 * together.
 */
@ContextConfiguration(classes = [JedisConfiguration, TestConfiguration, EchoConfiguration])
class NotificationSpec extends Specification {

  def pipelineStarter = Mock(PipelineStarter)
  @Autowired AbstractApplicationContext applicationContext
  @Autowired BuildJobPollingNotificationAgent notificationAgent
  @Autowired BuildJobPipelineIndexer pipelineIndexer

  def setupSpec() {
    System.setProperty("echo.baseUrl", "http://echo")
    System.setProperty("mayo.baseUrl", "http://mayo")
  }

  def cleanupSpec() {
    System.clearProperty("echo.baseUrl")
    System.clearProperty("mayo.baseUrl")
  }

  def setup() {
    def trigger = new Trigger("master1", "SPINNAKER-package-pond")
    pipelineIndexer.@pipelinesByTrigger[trigger] = [[
                                                        name    : "pipeline1",
                                                        triggers: [[type  : "jenkins",
                                                                    job   : "SPINNAKER-package-pond",
                                                                    master: "master1"]],
                                                        stages  : [[type: "bake"],
                                                                   [type: "deploy", cluster: [name: "bar"]]]
                                                    ]]

    notificationAgent.scheduler = Schedulers.test()

    applicationContext.beanFactory.with {
      registerSingleton "pipelineStarter", pipelineStarter
    }
  }

  def "jenkins trigger causes a pipeline to start"() {
    given:
    def latch = new CountDownLatch(1)
    pipelineStarter.start(*_) >> { latch.countDown() }

    expect:
    applicationContext.getBean(PipelineStarter) != null

    when:
    notificationAgent.filterEvents([
        [
            content: [
                project: [
                    name     : "SPINNAKER-package-pond",
                    lastBuild: [result: "SUCCESS", building: "false"]
                ],
                master : "master1"
            ]
        ]
    ])

    then:
    latch.await(1, TimeUnit.SECONDS)
  }
}

@Configuration
class TestConfiguration {
  @Bean PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() {
    new PropertyPlaceholderConfigurer()
  }

  @Bean ObjectMapper objectMapper() {
    new OrcaObjectMapper()
  }

  @Bean EchoEventPoller echoEventPoller() {
    [:] as EchoEventPoller
  }

  @Bean MayoService mayoService() {
    [:] as MayoService
  }

  @Bean ExecutionRepository executionRepository() {
    [:] as ExecutionRepository
  }
}

/**
 * This configuration class sets up embedded Redis for Jesque if a Redis URL is
 * not specified in system properties.
 */
@Configuration
class JedisConfiguration {
  @Bean
  @ConditionalOnExpression("#{systemProperties['redis.connection'] == null}")
  EmbeddedRedis redisServer() {
    EmbeddedRedis.embed()
  }

  @Bean
  @ConditionalOnBean(EmbeddedRedis)
  Config jesqueConfig(EmbeddedRedis redis) {
    new ConfigBuilder()
        .withHost("localhost")
        .withPort(redis.redisServer.port)
        .build()
  }

  @Bean
  @ConditionalOnBean(EmbeddedRedis)
  Pool<Jedis> jedisPool(EmbeddedRedis redis) {
    new JedisPool("localhost", redis.redisServer.port)
  }
}

