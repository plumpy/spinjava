/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

import static com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider.Details;

@Data
public class EcsLoadBalancerDetail implements Details {
  String account;
  String region;
  String name;
  String vpcId;
  String type = "aws";
  String loadBalancerType;
  List<String> securityGroups = new LinkedList<>();
  List<String> targetGroups = new LinkedList<>();
}
