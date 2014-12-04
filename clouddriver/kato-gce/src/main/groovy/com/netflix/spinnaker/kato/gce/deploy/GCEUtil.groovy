/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.netflix.frigga.NameValidation
import com.netflix.frigga.Names
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.gce.deploy.ops.ReplicaPoolBuilder
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.gce.security.GoogleCredentials
import java.util.concurrent.TimeUnit

class GCEUtil {
  static class Clock {
    long currentTimeMillis() {
      return System.currentTimeMillis()
    }
  }
  public static final String APPLICATION_NAME = "Spinnaker"
  public static OPERATIONS_POLLING_INTERVAL_FRACTION = 5

  // TODO(duftler): This list should not be static, but should also not be built on each call.
  static final List<String> baseImageProjects = ["centos-cloud", "coreos-cloud", "debian-cloud", "google-containers",
                                                 "opensuse-cloud", "rhel-cloud", "suse-cloud", "ubuntu-os-cloud"]

  static MachineType queryMachineType(String projectName, String zone, String machineTypeName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up machine type $machineTypeName..."
    def machineType = compute.machineTypes().list(projectName, zone).execute().getItems().find {
      it.getName() == machineTypeName
    }

    if (machineType) {
      return machineType
    } else {
      updateStatusAndThrowException("Machine type $machineTypeName not found.", task, phase)
    }
  }

  static Image querySourceImage(String projectName, String sourceImageName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up source image $sourceImageName..."

    def imageProjects = [projectName] + baseImageProjects
    def sourceImage = null

    def imageListBatch = compute.batch()
    def imageListCallback = new JsonBatchCallback<ImageList>() {
      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        updateStatusAndThrowException("Error locating $sourceImageName in these projects: $imageProjects: $e.message", task, phase)
      }

      @Override
      void onSuccess(ImageList imageList, HttpHeaders responseHeaders) throws IOException {
        for (def image : imageList.items) {
          if (image.name == sourceImageName) {
            sourceImage = image
          }
        }
      }
    }

    for (imageProject in imageProjects) {
      compute.images().list(imageProject).queue(imageListBatch, imageListCallback)
    }

    imageListBatch.execute()

    if (sourceImage) {
      return sourceImage
    } else {
      updateStatusAndThrowException("Source image $sourceImageName not found in any of these projects: $imageProjects.", task, phase)
    }
  }

  static Network queryNetwork(String projectName, String networkName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up network $networkName..."
    def network = compute.networks().list(projectName).execute().getItems().find {
      it.getName() == networkName
    }

    if (network) {
      return network
    } else {
      updateStatusAndThrowException("Network $networkName not found.", task, phase)
    }
  }

  static InstanceTemplate queryInstanceTemplate(String projectName, String instanceTemplateName, Compute compute) {
    compute.instanceTemplates().get(projectName, instanceTemplateName).execute()
  }

  static InstanceGroupManager queryManagedInstanceGroup(String projectName,
                                                        String zone,
                                                        String serverGroupName,
                                                        GoogleCredentials credentials,
                                                        ReplicaPoolBuilder replicaPoolBuilder,
                                                        String applicationName) {
    def credentialBuilder = credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, applicationName);

    replicapool.instanceGroupManagers().get(projectName, zone, serverGroupName).execute()
  }

  static List<InstanceGroupManager> queryManagedInstanceGroups(String projectName,
                                                               String region,
                                                               GoogleCredentials credentials,
                                                               ReplicaPoolBuilder replicaPoolBuilder,
                                                               String applicationName) {
    def credentialBuilder = credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, applicationName);
    def zones = getZonesFromRegion(projectName, region, credentials.compute)

    def allMIGSInRegion = zones.findResults {
      def localZoneName = getLocalName(it)

      replicapool.instanceGroupManagers().list(projectName, localZoneName).execute().getItems()
    }.flatten()

    allMIGSInRegion
  }

  static String getRegionFromZone(String projectName, String zone, Compute compute) {
    // Zone.getRegion() returns a full URL reference.
    def fullRegion = compute.zones().get(projectName, zone).execute().getRegion()
    // Even if getRegion() is changed to return just the unqualified region name, this will still work.
    getLocalName(fullRegion)
  }

  static List<String> getZonesFromRegion(String projectName, String region, Compute compute) {
    return compute.regions().get(projectName, region).execute().getZones()
  }

  static Operation waitForRegionalOperation(Compute compute, String projectName, String region, String operationName,
                                            long timeoutMillis) {
    return waitForOperation({compute.regionOperations().get(projectName, region, operationName).execute()},
        timeoutMillis, new Clock())
  }

  static Operation waitForGlobalOperation(Compute compute, String projectName, String operationName,
                                          long timeoutMillis) {
    return waitForOperation({compute.globalOperations().get(projectName, operationName).execute()}, timeoutMillis,
        new Clock())
  }

  // TODO(odedmeri): implement a more accurate way to achieve timeouts with polling.
  private static Operation waitForOperation(Closure getOperation, long timeoutMillis, Clock clock) {
    def deadline = clock.currentTimeMillis() + timeoutMillis
    long sleepIntervalMillis = timeoutMillis / OPERATIONS_POLLING_INTERVAL_FRACTION
    while (true) {
      Operation operation = getOperation()
      if (operation.getStatus() == "DONE") {
        return operation
      }
      def timeLeftUntilTimeoutMillis = deadline - clock.currentTimeMillis()
      if (timeLeftUntilTimeoutMillis <= 0) {
        break;
      }
      try {
        // TODO(odedmeri): Sleeping for timeLeftUntilTimeoutMillis will actually cause us to miss the deadline by one
        // extra polling. We should subtract a constant from it that will cover for the call on the next iteration.
        TimeUnit.MILLISECONDS.sleep(Math.min(sleepIntervalMillis, timeLeftUntilTimeoutMillis))
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return null
  }

  static AttachedDisk buildAttachedDisk(Image sourceImage, long diskSizeGb, String diskType) {
    def attachedDiskInitializeParams = new AttachedDiskInitializeParams(sourceImage: sourceImage.selfLink,
                                                                        diskSizeGb: diskSizeGb,)

    return new AttachedDisk(boot: true, autoDelete: true, type: diskType, initializeParams: attachedDiskInitializeParams)
  }

  static NetworkInterface buildNetworkInterface(Network network, String accessConfigName, String accessConfigType) {
    def accessConfig = new AccessConfig(name: accessConfigName, type: accessConfigType)

    return new NetworkInterface(network: network.selfLink, accessConfigs: [accessConfig])
  }

  static def getNextSequence(String clusterName,
                             String project,
                             String region,
                             GoogleCredentials credentials,
                             ReplicaPoolBuilder replicaPoolBuilder) {
    def maxSeqNumber = -1
    def managedInstanceGroups = GCEUtil.queryManagedInstanceGroups(project,
                                                                   region,
                                                                   credentials,
                                                                   replicaPoolBuilder,
                                                                   APPLICATION_NAME)

    for (def managedInstanceGroup : managedInstanceGroups) {
      def names = Names.parseName(managedInstanceGroup.getName())

      if (names.cluster == clusterName) {
        maxSeqNumber = Math.max(maxSeqNumber, names.sequence)
      }
    }

    String.format("%03d", ++maxSeqNumber)
  }

  static def combineAppStackDetail(String appName, String stack, String detail) {
    NameValidation.notEmpty(appName, "appName");

    // Use empty strings, not null references that output "null"
    stack = stack != null ? stack : "";

    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }

    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }

    return appName;
  }

  private static void updateStatusAndThrowException(String errorMsg, Task task, String phase) {
    task.updateStatus phase, errorMsg
    throw new GCEResourceNotFoundException(errorMsg)
  }

  public static String getLocalName(String fullUrl) {
    def urlParts = fullUrl.split("/")

    return urlParts[urlParts.length - 1]
  }

  static def buildHttpHealthCheck(String name, CreateGoogleNetworkLoadBalancerDescription.HealthCheck healthCheckDescription) {
    return new HttpHealthCheck(
        name: name,
        checkIntervalSec: healthCheckDescription.checkIntervalSec,
        timeoutSec: healthCheckDescription.timeoutSec,
        healthyThreshold: healthCheckDescription.healthyThreshold,
        unhealthyThreshold: healthCheckDescription.unhealthyThreshold,
        port: healthCheckDescription.port,
        requestPath: healthCheckDescription.requestPath)
  }

  // I know this is painfully similar to the method above. I will soon make a cleanup change to remove this ugliness.
  // TODO(bklingher): Clean this up.
  static def makeHttpHealthCheck(String name, CreateGoogleHttpLoadBalancerDescription.HealthCheck healthCheckDescription) {
    if (healthCheckDescription) {
      return new HttpHealthCheck(
          name: name,
          checkIntervalSec: healthCheckDescription.checkIntervalSec,
          timeoutSec: healthCheckDescription.timeoutSec,
          healthyThreshold: healthCheckDescription.healthyThreshold,
          unhealthyThreshold: healthCheckDescription.unhealthyThreshold,
          port: healthCheckDescription.port,
          requestPath: healthCheckDescription.requestPath)
    } else {
      return new HttpHealthCheck(
          name: name
      )
    }
  }
}
