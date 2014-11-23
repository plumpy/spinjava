


package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.kato.tasks.*
import com.netflix.spinnaker.orca.pipeline.LinearStage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class UpsertAmazonLoadBalancerStage extends LinearStage {

  public static final String MAYO_CONFIG_TYPE = "upsertAmazonLoadBalancer"

  UpsertAmazonLoadBalancerStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps(Stage stage) {
    def step1 = buildStep("upsertAmazonLoadBalancer", UpsertAmazonLoadBalancerTask)
    def step2 = buildStep("monitorUpsert", MonitorKatoTask)
    def step3 = buildStep("extrapolateUpsertResult", UpsertAmazonLoadBalancerResultObjectExtrapolationTask)
    def step4 = buildStep("forceCacheRefresh", UpsertAmazonLoadBalancerForceRefreshTask)
    def step5 = buildStep("sendNotification", NotifyEchoTask)
    [step1, step2, step3, step4, step5]
  }
}
