/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.stackdriver.controllers;

import com.netflix.kayenta.stackdriver.query.StackdriverQuery;
import com.netflix.kayenta.stackdriver.query.StackdriverSynchronousQueryProcessor;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/fetch/stackdriver")
@Slf4j
public class StackdriverFetchController {

  @Autowired
  StackdriverSynchronousQueryProcessor stackdriverSynchronousQueryProcessor;

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public String queryMetrics(@RequestParam(required = false) final String metricsAccountName,
                             @RequestParam(required = false) final String storageAccountName,
                             @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                             @ApiParam(defaultValue = "compute.googleapis.com/instance/cpu/utilization") @RequestParam String metricType,
                             @RequestParam(required = false) List<String> groupByFields, // metric.label.instance_name
                             @ApiParam(defaultValue = "myapp-v010-") @RequestParam String scope,
                             @ApiParam(defaultValue = "2017-05-01T15:13:00Z") @RequestParam String intervalStartTimeIso,
                             @ApiParam(defaultValue = "2017-05-02T15:27:00Z") @RequestParam String intervalEndTimeIso,
                             @ApiParam(defaultValue = "3600") @RequestParam String step) throws IOException {
    StackdriverQuery.StackdriverQueryBuilder stackdriverQueryBuilder =
      StackdriverQuery
        .builder()
        .metricsAccountName(metricsAccountName)
        .storageAccountName(storageAccountName)
        .metricSetName(metricSetName)
        .metricType(metricType)
        .scope(scope)
        .intervalStartTimeIso(intervalStartTimeIso)
        .intervalEndTimeIso(intervalEndTimeIso)
        .step(step);

    if (groupByFields != null) {
      stackdriverQueryBuilder.groupByFields(groupByFields);
    }

    return stackdriverSynchronousQueryProcessor.processQuery(stackdriverQueryBuilder.build());
  }
}
