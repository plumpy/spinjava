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
package com.netflix.spinnaker.kato.services
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.*
import com.google.common.collect.Iterables
import com.netflix.spinnaker.kato.model.aws.AutoScalingProcessType
import com.netflix.spinnaker.kato.model.aws.AwsResultsRetriever
import groovy.transform.Canonical

@Canonical
class AsgService {

  final ThrottleService throttleService
  final AmazonAutoScaling amazonAutoScaling

  void suspendProcesses(String asgName, Collection<AutoScalingProcessType> processes) {
    def request = new SuspendProcessesRequest(scalingProcesses: processes*.name(), autoScalingGroupName: asgName)
    amazonAutoScaling.suspendProcesses(request)
  }

  void resumeProcesses(String asgName, Collection<AutoScalingProcessType> processes) {
    def request = new ResumeProcessesRequest(scalingProcesses: processes*.name(), autoScalingGroupName: asgName)
    amazonAutoScaling.resumeProcesses(request)
  }

  AutoScalingGroup getAutoScalingGroup(String asgName) {
    Iterables.getOnlyElement(getAutoScalingGroups([asgName]), null)
  }

  List<AutoScalingGroup> getAutoScalingGroups(Collection<String> asgNames) {
    def retriever = new AwsResultsRetriever<AutoScalingGroup, DescribeAutoScalingGroupsRequest, DescribeAutoScalingGroupsResult>(throttleService) {
      @Override
      protected DescribeAutoScalingGroupsResult makeRequest(DescribeAutoScalingGroupsRequest request) {
        amazonAutoScaling.describeAutoScalingGroups(request)
      }
      @Override
      protected List<AutoScalingGroup> accessResult(DescribeAutoScalingGroupsResult result) {
        result.autoScalingGroups
      }
    }
    def request = new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: asgNames)
    retriever.retrieve(request)
  }
}
