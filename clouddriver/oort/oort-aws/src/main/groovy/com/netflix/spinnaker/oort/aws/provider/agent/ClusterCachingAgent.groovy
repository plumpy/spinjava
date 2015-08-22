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

package com.netflix.spinnaker.oort.aws.provider.agent

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsRequest
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.amazonaws.services.autoscaling.model.ScheduledUpdateGroupAction
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest
import com.amazonaws.services.ec2.model.Subnet
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.oort.aws.data.Keys
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.*
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.*
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.aws.provider.AwsProvider

@Slf4j
class ClusterCachingAgent implements CachingAgent, OnDemandAgent {
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(LAUNCH_CONFIGS.ns),
    INFORMATIVE.forType(INSTANCES.ns)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper

  ClusterCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${ClusterCachingAgent.simpleName}"
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  static class MutableCacheData implements CacheData {
    final String id
    int ttlSeconds = -1
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }

    public MutableCacheData(String id) {
      this.id = id
    }

    @JsonCreator
    public MutableCacheData(@JsonProperty("id") String id,
                            @JsonProperty("attributes") Map<String, Object> attributes,
                            @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
      this(id);
      this.attributes.putAll(attributes);
      this.relationships.putAll(relationships);
    }
  }

  @Override
  boolean handles(String type) {
    type == "AmazonServerGroup"
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("asgName")) {
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

    String asgName = data.asgName.toString()

    def autoScaling = amazonClientProvider.getAutoScaling(account, region, true)
    List<AutoScalingGroup> asgs = autoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName)
    ).autoScalingGroups

    Map<String, Collection<Map>> scalingPolicies = loadScalingPolicies(asgName)

    Map<String, Collection<Map>> scheduledActions = loadScheduledActions(asgName)

    Map<String, String> subnetMap = [:]
    asgs.each {
      if (it.getVPCZoneIdentifier()) {
        subnetMap.putAll(getSubnetToVpcIdMap(it.getVPCZoneIdentifier().split(',')))
      }
    }

    def cacheResult = buildCacheResult(asgs, scalingPolicies, scheduledActions, subnetMap, [:])
    def cacheData = new DefaultCacheData(
      Keys.getServerGroupKey(asgName, account.name, region),
      60 * 60,
      [
        cacheTime   : new Date(),
        cacheResults: objectMapper.writeValueAsString(cacheResult.cacheResults)
      ],
      [:]
    )
    providerCache.putCacheData(ON_DEMAND.ns, cacheData)

    cacheResult.cacheResults.values().each { Collection<CacheData> cacheDatas ->
      cacheDatas.each {
        ((MutableCacheData) it).ttlSeconds = 60
      }
    }

    Map<String, Collection<String>> evictions = asgs ? [:] : [
      (SERVER_GROUPS.ns): [
        Keys.getServerGroupKey(asgName, account.name, region)
      ]
    ]

    log.info("onDemand cache refresh (data: ${data}, evictions: ${evictions})")
    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(), cacheResult: cacheResult, evictions: evictions
    )
  }

  private Map<String, CacheData> cache() {
    [:].withDefault { String id -> new MutableCacheData(id) }
  }

  Map<String, String> getSubnetToVpcIdMap(String... subnetIds) {
    boolean bypassEdda = subnetIds.length > 0
    def ec2 = amazonClientProvider.getAmazonEC2(account, region, bypassEdda)
    Map<String, String> subnetMap = [:]
    def request = new DescribeSubnetsRequest()
    if (subnetIds.length > 0) {
      request.withSubnetIds(subnetIds)
    }
    for (Subnet subnet : ec2.describeSubnets(request).subnets) {
      String existing = subnetMap.put(subnet.subnetId, subnet.vpcId)
      if (existing != null && existing != subnet.vpcId) {
        throw new RuntimeException("Unexpected non unique subnetId to vpcId mapping")
      }
    }
    subnetMap
  }

  private AutoScalingGroupsResults loadAutoScalingGroups() {
    log.info("Describing auto scaling groups in ${agentType}")
    def autoScaling = amazonClientProvider.getAutoScaling(account, region)

    def request = new DescribeAutoScalingGroupsRequest()
    Long start = null

    List<AutoScalingGroup> asgs = []
    while (true) {
      def resp = autoScaling.describeAutoScalingGroups(request)
      if (!start) {
        start = EddaSupport.parseLastModified(amazonClientProvider.lastResponseHeaders?.get("last-modified")?.get(0))
      }
      asgs.addAll(resp.autoScalingGroups)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }

    new AutoScalingGroupsResults(start: start, asgs: asgs)
  }

  private Map<String, List<Map>> loadScalingPolicies() {
    loadScalingPolicies(null)
  }

  private Map<String, List<Map>> loadScalingPolicies(String asgName) {
    log.info("Describing scaling policies in ${agentType}")
    def autoScaling = amazonClientProvider.getAutoScaling(account, region)

    def request = new DescribePoliciesRequest()
    if (asgName) {
      request.withAutoScalingGroupName(asgName)
    }
    List<ScalingPolicy> scalingPolicies = []
    while (true) {
      def resp = autoScaling.describePolicies(request)
      scalingPolicies.addAll(resp.scalingPolicies)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }
    def alarmNames = []
    if (asgName) {
      alarmNames = scalingPolicies.findResults { it.alarms.findResults { it.alarmName }}.flatten().unique()
    }
    Map<String, Map> alarms = loadAlarms(alarmNames)

    scalingPolicies
        .findResults { buildScalingPolicy(it, alarms) }
        .groupBy { it.autoScalingGroupName }
  }

  private Map<String, List<Map>> loadScheduledActions() {
    loadScheduledActions(null)
  }

  private Map<String, List<Map>> loadScheduledActions(String asgName) {
    log.info("Describing scheduled actions in ${agentType}")
    def autoScaling = amazonClientProvider.getAutoScaling(account, region)

    def request = new DescribeScheduledActionsRequest()
    if (asgName) {
      request.withAutoScalingGroupName(asgName)
    }
    List<ScheduledUpdateGroupAction> scheduledActions = []
    while (true) {
      def resp = autoScaling.describeScheduledActions(request)
      scheduledActions.addAll(resp.scheduledUpdateGroupActions)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }
    scheduledActions
        .findResults { toMap(it) }
        .groupBy { it.autoScalingGroupName }
  }

  private Map<String, Object> toMap(obj) {
    objectMapper.convertValue(obj, Map)
  }

  private Map<String, Map> loadAlarms(List alarmNames) {
    log.info("Describing alarms in ${agentType}")
    def cloudWatch = amazonClientProvider.getCloudWatch(account, region)

    def request = new DescribeAlarmsRequest()
    if (alarmNames.size()) {
      request.withAlarmNames(alarmNames)
    }
    List<MetricAlarm> alarms = []
    while (true) {
      def resp = cloudWatch.describeAlarms(request)
      alarms.addAll(resp.metricAlarms)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }
    alarms.collectEntries { [(it.alarmArn) : toMap(it) ]}
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    def autoScalingGroupsResult = loadAutoScalingGroups()
    def scalingPolicies = loadScalingPolicies()
    def scheduledActions = loadScheduledActions()

    Long start = autoScalingGroupsResult.start
    List<AutoScalingGroup> asgs = autoScalingGroupsResult.asgs

    def evictableOnDemandCacheDatas = []
    def usableOnDemandCacheDatas = []
    providerCache.getAll(ON_DEMAND.ns, asgs.collect { Keys.getServerGroupKey(it.autoScalingGroupName, account.name, region) }).each {
      if (it.attributes.cacheTime < start) {
        evictableOnDemandCacheDatas << it
      } else {
        usableOnDemandCacheDatas << it
      }
    }

    if (evictableOnDemandCacheDatas) {
      log.info("Evicting onDemand cache keys (${evictableOnDemandCacheDatas.collect { "${it.id}/${start - it.attributes.cacheTime}ms"}.join(", ")})")
      providerCache.evictDeletedItems(ON_DEMAND.ns, evictableOnDemandCacheDatas*.id)
    }

    CacheResult result = buildCacheResult(asgs, scalingPolicies, scheduledActions, getSubnetToVpcIdMap(), usableOnDemandCacheDatas.collectEntries { [it.id, it] })
    if (start) {
      long drift = new Date().time - start
      log.info("${agentType}/drift - $drift milliseconds")
    }
    def cacheResults = result.cacheResults
    log.info("Caching ${cacheResults[APPLICATIONS.ns]?.size()} applications in ${agentType}")
    log.info("Caching ${cacheResults[CLUSTERS.ns]?.size()} clusters in ${agentType}")
    log.info("Caching ${cacheResults[SERVER_GROUPS.ns]?.size()} server groups in ${agentType}")
    log.info("Caching ${cacheResults[LOAD_BALANCERS.ns]?.size()} load balancers in ${agentType}")
    log.info("Caching ${cacheResults[LAUNCH_CONFIGS.ns]?.size()} launch configs in ${agentType}")
    log.info("Caching ${cacheResults[INSTANCES.ns]?.size()} instances in ${agentType}")
    result
  }

  private CacheResult buildCacheResult(Collection<AutoScalingGroup> asgs,
                                       Map<String, List<Map>> scalingPolicies,
                                       Map<String, List<Map>> scheduledActions,
                                       Map<String, String> subnetMap,
                                       Map<String, CacheData> onDemandCacheDataByAsg) {
    Map<String, CacheData> applications = cache()
    Map<String, CacheData> clusters = cache()
    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> loadBalancers = cache()
    Map<String, CacheData> launchConfigs = cache()
    Map<String, CacheData> instances = cache()

    for (AutoScalingGroup asg : asgs) {
      def onDemandCacheData = onDemandCacheDataByAsg ? onDemandCacheDataByAsg[Keys.getServerGroupKey(asg.autoScalingGroupName, account.name, region)] : null
      if (onDemandCacheData) {
        log.info("Using onDemand cache value (${onDemandCacheData.id})")

        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandCacheData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() {})
        cache(cacheResults["applications"], applications)
        cache(cacheResults["clusters"], clusters)
        cache(cacheResults["serverGroups"], serverGroups)
        cache(cacheResults["loadBalancers"], loadBalancers)
        cache(cacheResults["launchConfigs"], launchConfigs)
        cache(cacheResults["instances"], instances)
      } else {
        try {
          AsgData data = new AsgData(asg, scalingPolicies[asg.autoScalingGroupName], scheduledActions[asg.autoScalingGroupName], account.name, region, subnetMap)
          cacheApplication(data, applications)
          cacheCluster(data, clusters)
          cacheServerGroup(data, serverGroups)
          cacheLaunchConfig(data, launchConfigs)
          cacheInstances(data, instances)
          cacheLoadBalancers(data, loadBalancers)
        } catch (Exception ex) {
          log.warn("Failed to cache ${asg.autoScalingGroupName} in ${account.name}/${region}", ex)
        }
      }
    }

    new DefaultCacheResult(
      (APPLICATIONS.ns): applications.values(),
      (CLUSTERS.ns): clusters.values(),
      (SERVER_GROUPS.ns): serverGroups.values(),
      (LOAD_BALANCERS.ns): loadBalancers.values(),
      (LAUNCH_CONFIGS.ns): launchConfigs.values(),
      (INSTANCES.ns): instances.values())
  }

  private void cache(List<CacheData> data, Map<String, CacheData> cacheDataById) {
    data.each {
      def existingCacheData = cacheDataById[it.id]
      if (!existingCacheData) {
        cacheDataById[it.id] = it
      } else {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      }
    }
  }

  private void cacheApplication(AsgData data, Map<String, CacheData> applications) {
    applications[data.appName].with {
      attributes.name = data.name.app
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerNames)
    }
  }

  private void cacheCluster(AsgData data, Map<String, CacheData> clusters) {
    clusters[data.cluster].with {
      attributes.name = data.name.cluster
      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerNames)
    }
  }

  private void cacheServerGroup(AsgData data, Map<String, CacheData> serverGroups) {
    serverGroups[data.serverGroup].with {
      attributes.asg = objectMapper.convertValue(data.asg, ATTRIBUTES)
      attributes.region = region
      attributes.name = data.asg.autoScalingGroupName
      attributes.launchConfigName = data.asg.launchConfigurationName
      attributes.zones = data.asg.availabilityZones
      attributes.instances = data.asg.instances
      attributes.vpcId = data.vpcId
      attributes.scalingPolicies = data.scalingPolicies
      attributes.scheduledActions = data.scheduledActions

      relationships[APPLICATIONS.ns].add(data.appName)
      relationships[CLUSTERS.ns].add(data.cluster)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerNames)
      relationships[LAUNCH_CONFIGS.ns].add(data.launchConfig)
      relationships[INSTANCES.ns].addAll(data.instanceIds)
    }
  }

  private void cacheLaunchConfig(AsgData data, Map<String, CacheData> launchConfigs) {
    launchConfigs[data.launchConfig].with {
      relationships[SERVER_GROUPS.ns].add(data.serverGroup)
    }
  }

  private void cacheInstances(AsgData data, Map<String, CacheData> instances) {
    for (Instance instance : data.asg.instances) {
      instances[Keys.getInstanceKey(instance.instanceId, account.name, region)].with {
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      }
    }
  }

  private void cacheLoadBalancers(AsgData data, Map<String, CacheData> loadBalancers) {
    for (String loadBalancerName : data.loadBalancerNames) {
      loadBalancers[loadBalancerName].with {
        relationships[APPLICATIONS.ns].add(data.appName)
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      }
    }
  }

  private Map buildScalingPolicy(ScalingPolicy scalingPolicy, Map<String, Map> metricAlarms) {
    Map policy = objectMapper.convertValue(scalingPolicy, Map)
    policy.alarms = scalingPolicy.alarms.findResults {
      metricAlarms[it.alarmARN]
    }
    policy
  }

  private static class AutoScalingGroupsResults {
    Long start
    List<AutoScalingGroup> asgs
  }

  private static class AsgData {
    final AutoScalingGroup asg
    final Names name
    final String appName
    final String cluster
    final String serverGroup
    final String vpcId
    final String launchConfig
    final Set<String> loadBalancerNames
    final Set<String> instanceIds
    final List<Map> scalingPolicies
    final List<Map> scheduledActions

    public AsgData(AutoScalingGroup asg,
                   List<Map> scalingPolicies,
                   List<Map> scheduledActions,
                   String account,
                   String region,
                   Map<String, String> subnetMap) {
      this.asg = asg
      this.scalingPolicies = scalingPolicies ?: []
      this.scheduledActions = scheduledActions ?: []

      name = Names.parseName(asg.autoScalingGroupName)
      appName = Keys.getApplicationKey(name.app)
      cluster = Keys.getClusterKey(name.cluster, name.app, account)
      serverGroup = Keys.getServerGroupKey(asg.autoScalingGroupName, account, region)
      String vpcId = null
      if (asg.getVPCZoneIdentifier()) {
        String[] subnets = asg.getVPCZoneIdentifier().split(',')
        Set<String> vpcIds = subnets.collect { subnetMap[it] }
        if (vpcIds.size() != 1) {
          throw new RuntimeException("failed to resolve one vpc (found ${vpcIds}) for subnets ${subnets} in ASG ${asg.autoScalingGroupName} account ${account} region ${region}")
        }
        vpcId = vpcIds.first()
      }
      this.vpcId = vpcId
      launchConfig = Keys.getLaunchConfigKey(asg.launchConfigurationName, account, region)

      loadBalancerNames = (asg.loadBalancerNames.collect { Keys.getLoadBalancerKey(it, account, region, vpcId) } as Set).asImmutable()
      instanceIds = (asg.instances.instanceId.collect { Keys.getInstanceKey(it, account, region) } as Set).asImmutable()
    }
  }
}
