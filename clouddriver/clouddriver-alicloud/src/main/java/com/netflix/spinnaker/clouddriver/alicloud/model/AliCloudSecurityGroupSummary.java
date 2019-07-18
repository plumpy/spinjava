/*
 * Copyright 2019 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.model;

import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;

/**
 * @author: luoguan
 * @create: 2019-06-11 09:54
 */
public class AliCloudSecurityGroupSummary implements SecurityGroupSummary {

  String name;
  String id;
  String vpcId;

  public AliCloudSecurityGroupSummary(String name, String id, String vpcId) {
    this.name = name;
    this.id = id;
    this.vpcId = vpcId;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getVpcId() {
    return vpcId;
  }
}
