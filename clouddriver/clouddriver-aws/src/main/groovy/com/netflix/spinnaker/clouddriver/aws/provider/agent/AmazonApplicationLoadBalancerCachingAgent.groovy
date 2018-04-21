/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesResult
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupAttributesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest
import com.amazonaws.services.elasticloadbalancingv2.model.Listener
import com.amazonaws.services.elasticloadbalancingv2.model.ListenerNotFoundException
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancerNotFoundException
import com.amazonaws.services.elasticloadbalancingv2.model.Rule
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupAttribute
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApi
import com.netflix.spinnaker.clouddriver.aws.model.InstanceTargetGroupState
import com.netflix.spinnaker.clouddriver.aws.model.InstanceTargetGroups
import com.netflix.spinnaker.clouddriver.aws.model.edda.EddaRule
import com.netflix.spinnaker.clouddriver.aws.model.edda.TargetGroupAttributes
import com.netflix.spinnaker.clouddriver.aws.model.edda.TargetGroupHealth
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent
import retrofit.RetrofitError

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS

class AmazonApplicationLoadBalancerCachingAgent extends AbstractAmazonLoadBalancerCachingAgent implements HealthProvidingCachingAgent {
  final EddaApi eddaApi
  final EddaTimeoutConfig eddaTimeoutConfig

  static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
    AUTHORITATIVE.forType(TARGET_GROUPS.ns),
    AUTHORITATIVE.forType(HEALTH.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ])

  private static final String HEALTH_ID = "aws-load-balancer-v2-target-group-instance-health"

  AmazonApplicationLoadBalancerCachingAgent(AmazonCloudProvider amazonCloudProvider,
                                            AmazonClientProvider amazonClientProvider,
                                            NetflixAmazonCredentials account,
                                            String region,
                                            EddaApi eddaApi,
                                            ObjectMapper objectMapper,
                                            Registry registry,
                                            EddaTimeoutConfig eddaTimeoutConfig) {
    super(amazonCloudProvider, amazonClientProvider, account, region, objectMapper, registry)
    this.eddaApi = eddaApi
    this.eddaTimeoutConfig = eddaTimeoutConfig
  }

  @Override
  String getHealthId() {
    HEALTH_ID
  }

  @Override
  Optional<Map<String, String>> getCacheKeyPatterns() {
    return [
      (LOAD_BALANCERS.ns): Keys.getLoadBalancerKey('*', account.name, region, 'vpc-????????', '*')
    ]
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("loadBalancerName")) {
      return null
    }
    if (!data.containsKey("loadBalancerType") || !data.loadBalancerType.equals("application")) {
      return null
    }
    if (!data.containsKey("account")) {
      return null
    }
    if (!data.containsKey("region")) {
      return null
    }
    if (account.name != data.account) {
      return null
    }

    if (region != data.region) {
      return null
    }

    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(account, region, true)

    LoadBalancer loadBalancer = metricsSupport.readData {
      try {
        return loadBalancing.describeLoadBalancers(
          new DescribeLoadBalancersRequest().withNames([data.loadBalancerName as String])
        ).loadBalancers.get(0)
      } catch (LoadBalancerNotFoundException ignored) {
        return null
      }
    }

    List<TargetGroup> targetGroups = metricsSupport.readData {
      return loadBalancing.describeTargetGroups(
        new DescribeTargetGroupsRequest().withLoadBalancerArn(loadBalancer.loadBalancerArn)
      ).targetGroups
    }

    TargetGroupAssociations targetGroupAssociations = this.buildTargetGroupAssociations(loadBalancing, targetGroups, false)
    ListenerAssociations listenerAssociations = this.buildListenerAssociations(loadBalancing, [loadBalancer], false)

    def cacheResult = metricsSupport.transformData {
      buildCacheResult(
        [loadBalancer],
        targetGroups,
        targetGroupAssociations,
        listenerAssociations,
        [:],
        System.currentTimeMillis(),
        []
      )
    }
    if (cacheResult.cacheResults.values().flatten().isEmpty()) {
      // avoid writing an empty onDemand cache record (instead delete any that may have previously existed)
      providerCache.evictDeletedItems(ON_DEMAND.ns, [
          Keys.getLoadBalancerKey(
            data.loadBalancerName as String,
            account.name,
            region,
            data.vpcId as String,
            data.loadBalancerType as String
          )
      ])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          Keys.getLoadBalancerKey(
            data.loadBalancerName as String,
            account.name, region,
            loadBalancer ? loadBalancer.getVpcId() : null,
            data.loadBalancerType as String
          ),
          10 * 60,
          [
            cacheTime   : new Date(),
            cacheResults: objectMapper.writeValueAsString(cacheResult.cacheResults)
          ],
          [:]
        )
        providerCache.putCacheData(ON_DEMAND.ns, cacheData)
      }
    }
    Map<String, Collection<String>> evictions = loadBalancer ? [:] : [
      (LOAD_BALANCERS.ns): [
        Keys.getLoadBalancerKey(
          data.loadBalancerName as String,
          account.name,
          region,
          data.vpcId as String,
          data.loadBalancerType as String
        )
      ]
    ]

    log.info("onDemand cache refresh (data: ${data}, evictions: ${evictions})")
    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getAgentType(), cacheResult: cacheResult, evictions: evictions
    )
  }

  TargetGroupAssociations buildTargetGroupAssociations(AmazonElasticLoadBalancing loadBalancing, List<TargetGroup> allTargetGroups, boolean useEdda) {
    // Get all the target group health and attributes
    Map<String, List<TargetHealthDescription>> targetGroupArnToHealths
    Map<String, List<TargetGroupAttribute>> targetGroupArnToAttributes
    if (useEdda) {
      List<TargetGroupAttributes> targetGroupAttributesList = eddaApi.targetGroupAttributes()
      List<TargetGroupHealth> targetGroupHealthList = eddaApi.targetGroupHealth()
      targetGroupArnToAttributes = targetGroupAttributesList.collectEntries { [(it.targetGroupArn): it.attributes] }
      targetGroupArnToHealths = targetGroupHealthList.collectEntries { [(it.targetGroupArn): it.health]}
    } else {
      targetGroupArnToHealths = new HashMap<String, List<TargetHealthDescription>>()
      targetGroupArnToAttributes = new HashMap<String, List<TargetGroupAttribute>>()
      for (TargetGroup targetGroup : allTargetGroups) {
        List<TargetHealthDescription> targetHealthDescriptions = loadBalancing.describeTargetHealth(
          new DescribeTargetHealthRequest().withTargetGroupArn(targetGroup.targetGroupArn)
        ).targetHealthDescriptions
        targetGroupArnToHealths.put(targetGroup.targetGroupArn, targetHealthDescriptions)
        List<TargetGroupAttribute> targetGroupAttributes = loadBalancing.describeTargetGroupAttributes(
          new DescribeTargetGroupAttributesRequest().withTargetGroupArn(targetGroup.targetGroupArn)
        ).attributes
        targetGroupArnToAttributes.put(targetGroup.targetGroupArn, targetGroupAttributes)
      }
    }

    return [
      targetGroupArnToAttributes: targetGroupArnToAttributes,
      targetGroupArnToHealths: targetGroupArnToHealths
    ]
  }

  ListenerAssociations buildListenerAssociations(AmazonElasticLoadBalancing loadBalancing, List<LoadBalancer> allLoadBalancers, boolean useEdda) {
    Map<String, List<Listener>> loadBalancerArnToListeners = [:]
    Map<Listener, List<Rule>> listenerToRules = new HashMap<>()
    for (LoadBalancer lb : allLoadBalancers) {
      // Add the listeners
      List<Listener> listenerData = new ArrayList<>()
      if (useEdda) {
        try {
          listenerData = eddaApi.listeners(lb.loadBalancerName)
        } catch (RetrofitError ignore) {
          // edda caches load balancers and listeners on a different refresh cycle, so there is a very small chance
          // right after creating a load balancer that the listeners are not in the cache yet. . We don't want to fail
          // the whole cache refresh; we can just get the listeners on the next cycle.
        }
      } else {
        DescribeListenersRequest describeListenersRequest = new DescribeListenersRequest(loadBalancerArn: lb.loadBalancerArn)
        while (true) {
          DescribeListenersResult result = loadBalancing.describeListeners(describeListenersRequest)
          listenerData.addAll(result.listeners)
          if (result.nextMarker) {
            describeListenersRequest.withMarker(result.nextMarker)
          } else {
            break
          }
        }
      }
      loadBalancerArnToListeners.put(lb.loadBalancerArn, listenerData)
      if (useEdda) {
        try {
          List<EddaRule> rules = eddaApi.rules(lb.loadBalancerName)
          Map<String, Listener> listenerByListenerArn = listenerData.collectEntries { [(it.listenerArn): it] }
          for (EddaRule eddaRule : rules) {
            Listener listener = listenerByListenerArn.get(eddaRule.listenerArn)
            listenerToRules.put(listener, eddaRule.rules)
          }
        } catch (Exception e) {
          log.error("Failed to load load balancer rules for ${account.name} ${region} ${lb.loadBalancerName}")
        }
      } else {
        for (listener in listenerData) {
          listenerToRules[listener] = []
          try {
            DescribeRulesRequest describeRulesRequest = new DescribeRulesRequest(listenerArn: listener.listenerArn)
            DescribeRulesResult result = loadBalancing.describeRules(describeRulesRequest)
            listenerToRules.get(listener).addAll(result.rules)
          } catch (ListenerNotFoundException ignore) {
            // should be fine
          }
        }
      }
    }

    return [
      loadBalancerArnToListeners: loadBalancerArnToListeners,
      listenerToRules: listenerToRules
    ]
  }

  @Override
  CacheResult loadDataInternal(ProviderCache providerCache) {
    AmazonElasticLoadBalancing loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(account, region)
    boolean useEdda = account.eddaEnabled && eddaTimeoutConfig.albEnabled

    Long start = useEdda ? null : System.currentTimeMillis()


    // Get all the load balancers
    List<LoadBalancer> allLoadBalancers = []
    DescribeLoadBalancersRequest describeLoadBalancerRequest = new DescribeLoadBalancersRequest()
    while (true) {
      def resp = loadBalancing.describeLoadBalancers(describeLoadBalancerRequest)
      if (useEdda) {
        start = amazonClientProvider.lastModified ?: 0
      }

      allLoadBalancers.addAll(resp.loadBalancers)
      if (resp.nextMarker) {
        describeLoadBalancerRequest.withMarker(resp.nextMarker)
      } else {
        break
      }
    }

    // Get all the target groups
    List<TargetGroup> allTargetGroups = []
    DescribeTargetGroupsRequest describeTargetGroupsRequest = new DescribeTargetGroupsRequest()
    while (true) {
      def resp = loadBalancing.describeTargetGroups(describeTargetGroupsRequest)
      allTargetGroups.addAll(resp.targetGroups)
      if (resp.nextMarker) {
        describeTargetGroupsRequest.withMarker(resp.nextMarker)
      } else {
        break
      }
    }

    def targetGroupAssociations = this.buildTargetGroupAssociations(loadBalancing, allTargetGroups, useEdda)
    def listenerAssociations = this.buildListenerAssociations(loadBalancing, allLoadBalancers, useEdda)

    if (!start) {
      if (useEdda && allTargetGroups) {
        log.warn("${agentType} did not receive lastModified value in response metadata")
      }
      start = System.currentTimeMillis()
    }

    def evictableOnDemandCacheDatas = []
    def usableOnDemandCacheDatas = []
    providerCache.getAll(ON_DEMAND.ns, allLoadBalancers.collect {
      Keys.getLoadBalancerKey(it.loadBalancerName, account.name, region, it.getVpcId(), it.getType()) }).each {
      if (it.attributes.cacheTime < start) {
//        evictableOnDemandCacheDatas << it
      } else {
        usableOnDemandCacheDatas << it
      }
    }

    return buildCacheResult(allLoadBalancers,
      allTargetGroups,
      targetGroupAssociations,
      listenerAssociations,
      usableOnDemandCacheDatas.collectEntries { [it.id, it] },
      start,
      evictableOnDemandCacheDatas
    )
  }

  private CacheResult buildCacheResult(Collection<LoadBalancer> allLoadBalancers,
                                       Collection<TargetGroup> allTargetGroups,
                                       TargetGroupAssociations targetGroupAssociations,
                                       ListenerAssociations listenerAssociations,
                                       Map<String, CacheData> onDemandCacheDataByLb,
                                       long start,
                                       Collection<CacheData> evictableOnDemandCacheDatas) {
    Map<String, CacheData> loadBalancers = CacheHelpers.cache()
    Map<String, CacheData> targetGroups = CacheHelpers.cache()

    for (LoadBalancer lb : allLoadBalancers) {
      String loadBalancerKey = Keys.getLoadBalancerKey(lb.loadBalancerName, account.name, region, lb.vpcId, lb.type)

      def onDemandCacheData = onDemandCacheDataByLb ? onDemandCacheDataByLb[loadBalancerKey] : null
      if (onDemandCacheData) {
        log.info("Using onDemand cache value (${onDemandCacheData.id})")

        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandCacheData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() {})
        CacheHelpers.cache(cacheResults["loadBalancers"], loadBalancers)
        CacheHelpers.cache(cacheResults["targetGroups"], targetGroups)
      } else {
        Map<String, Object> lbAttributes = objectMapper.convertValue(lb, ATTRIBUTES)

        // Type is already used for provider name, so rename the field
        lbAttributes.loadBalancerType = lbAttributes.type
        lbAttributes.remove("type")

        // Translate availabilityZones to the format we expect
        List<String> availabilityZones = new ArrayList<String>()
        List<String> subnets = new ArrayList<String>()
        ((List<Map<String, String>>)lbAttributes.availabilityZones).each { az ->
          availabilityZones.push(az.zoneName)
          subnets.push(az.subnetId)
        }
        lbAttributes.subnets = subnets
        lbAttributes.availabilityZones = availabilityZones


        def listeners = []
        Set<String> allTargetGroupKeys = []
        String vpcId = Keys.parse(loadBalancerKey).vpcId
        def listenerData = listenerAssociations.loadBalancerArnToListeners.get(lb.loadBalancerArn)
        for (Listener listener : listenerData) {

          Map<String, Object> listenerAttributes = objectMapper.convertValue(listener, ATTRIBUTES)
          listenerAttributes.loadBalancerName = ArnUtils.extractLoadBalancerName((String)listenerAttributes.loadBalancerArn).get()
          listenerAttributes.remove('loadBalancerArn')
          for (Map<String, Object> action : (List<Map<String, String>>)listenerAttributes.defaultActions) {
            if (!action.targetGroupArn) {
              continue
            }

            String targetGroupName = ArnUtils.extractTargetGroupName(action.targetGroupArn as String).get()
            action.targetGroupName = targetGroupName
            action.remove("targetGroupArn")

            allTargetGroupKeys.add(Keys.getTargetGroupKey(targetGroupName, account.name, region, vpcId))
          }

          // add the rules to the listener
          List<Object> rules = new ArrayList<>()
          for (Rule rule : listenerAssociations.listenerToRules.get(listener)) {
            Map<String, Object> ruleAttributes = objectMapper.convertValue(rule, ATTRIBUTES)
            for (Map<String, String> action : (List<Map<String, String>>)ruleAttributes.actions) {
              if (!action.targetGroupArn) {
                continue
              }

              String targetGroupName = ArnUtils.extractTargetGroupName(action.targetGroupArn).get()
              action.targetGroupName = targetGroupName
              action.remove("targetGroupArn")

              allTargetGroupKeys.add(Keys.getTargetGroupKey(targetGroupName, account.name, region, vpcId))
            }

            rules.push(ruleAttributes)
          }
          listenerAttributes.rules = rules

          listeners.push(listenerAttributes)
        }

        lbAttributes.listeners = listeners

        loadBalancers[loadBalancerKey].with {
          attributes.putAll(lbAttributes)
          relationships[TARGET_GROUPS.ns].addAll(allTargetGroupKeys)
        }
      }
    }

    List<InstanceTargetGroupState> instanceTargetGroupStates = []
    for (TargetGroup targetGroup : allTargetGroups) {
      String targetGroupId = Keys.getTargetGroupKey(targetGroup.targetGroupName, account.name, region, targetGroup.vpcId)
      // Get associated load balancer keys
      Collection<String> loadBalancerIds = targetGroup.loadBalancerArns.collect {
        String lbName = ArnUtils.extractLoadBalancerName(it).get()
        String lbType = ArnUtils.extractLoadBalancerType(it)
        Keys.getLoadBalancerKey(lbName, account.name, region, targetGroup.vpcId, lbType)
      }

      // Collect health information for the target group and instance ids
      List<String> instanceIds = new ArrayList<String>()
      List<TargetHealthDescription> targetHealthDescriptions = targetGroupAssociations.targetGroupArnToHealths.get(targetGroup.targetGroupArn)
      for (TargetHealthDescription targetHealthDescription : targetHealthDescriptions) {
        instanceTargetGroupStates << new InstanceTargetGroupState(
          targetHealthDescription.target.id,
          ArnUtils.extractTargetGroupName(targetGroup.targetGroupArn).get(),
          targetHealthDescription.targetHealth.state,
          targetHealthDescription.targetHealth.reason,
          targetHealthDescription.targetHealth.description
        )
        instanceIds << targetHealthDescription.target.id
      }

      // Get target group attributes
      Map<String, String> tgAttributes = targetGroupAssociations.targetGroupArnToAttributes.get(targetGroup.targetGroupArn)?.collectEntries {
        [(it.key): it.value]
      }

      Map<String, Object> tgCacheAttributes = objectMapper.convertValue(targetGroup, ATTRIBUTES)
      tgCacheAttributes.put('instances', instanceIds)
      tgCacheAttributes.put('attributes', tgAttributes)
      tgCacheAttributes.loadBalancerNames = tgCacheAttributes.loadBalancerArns.collect { String lbArn ->
        ArnUtils.extractLoadBalancerName(lbArn).get()
      }
      tgCacheAttributes.remove('loadBalancerArns')

      // Cache target group
      targetGroups[targetGroupId].with {
        attributes.putAll(tgCacheAttributes)
        relationships[LOAD_BALANCERS.ns].addAll(loadBalancerIds)
      }
      for (String loadBalancerId : loadBalancerIds) {
        loadBalancers[loadBalancerId].with {
          relationships[TARGET_GROUPS.ns].add(targetGroupId)
        }
      }
    }

    // Build health cache
    // Have to do this separately because an instance can be in multiple target groups
    List<InstanceTargetGroups> itgs = InstanceTargetGroups.fromInstanceTargetGroupStates(instanceTargetGroupStates)
    Collection<CacheData> tgHealths = []
    Collection<CacheData> instances = []

    for (InstanceTargetGroups itg in itgs) {
      String instanceId = Keys.getInstanceKey(itg.instanceId, account.name, region)
      String healthId = Keys.getInstanceHealthKey(itg.instanceId, account.name, region, healthId)
      Map<String, Object> attributes = objectMapper.convertValue(itg, ATTRIBUTES)
      Map<String, Collection<String>> relationships = [(INSTANCES.ns): [instanceId]]
      tgHealths.add(new DefaultCacheData(healthId, attributes, relationships))
      instances.add(new DefaultCacheData(instanceId, [:], [(HEALTH.ns): [healthId]]))
    }


    log.info("Caching ${loadBalancers.size()} load balancers in ${agentType}")
    if (evictableOnDemandCacheDatas) {
      log.info("Evicting onDemand cache keys (${evictableOnDemandCacheDatas.collect { "${it.id}/${start - (long)it.attributes.cacheTime}ms"}.join(", ")})")
    }
    new DefaultCacheResult([
      (LOAD_BALANCERS.ns):  loadBalancers.values(),
      (TARGET_GROUPS.ns): targetGroups.values(),
      (HEALTH.ns): tgHealths,
      (INSTANCES.ns): instances
    ],[
      (ON_DEMAND.ns): evictableOnDemandCacheDatas*.id])
  }

  static class ListenerAssociations {
    Map<String, List<Listener>> loadBalancerArnToListeners
    Map<Listener, List<Rule>> listenerToRules
  }

  static class TargetGroupAssociations {
    Map<String, List<TargetHealthDescription>> targetGroupArnToHealths
    Map<String, List<TargetGroupAttribute>> targetGroupArnToAttributes
  }
}

