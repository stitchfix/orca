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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractWaitForInstanceHealthChangeTask implements RetryableTask {
  long backoffPeriod = 5000
  long timeout = 3600000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    if (stage.context.interestingHealthProviderNames != null && ((List)stage.context.interestingHealthProviderNames).isEmpty()) {
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }

    String region = stage.context.region as String
    String account = (stage.context.account ?: stage.context.credentials) as String
    List<String> healthProviderTypesToCheck = stage.context.interestingHealthProviderNames as List<String>

    def instanceIds = getInstanceIds(stage)
    if (!instanceIds) {
      return new DefaultTaskResult(ExecutionStatus.TERMINAL)
    }

    def stillRunning = instanceIds.find {
      def instance = getInstance(account, region, it)
      def healths = HealthHelper.filterHealths(instance, healthProviderTypesToCheck)

      return !hasSucceeded(healths)
    }

    return new DefaultTaskResult(stillRunning ? ExecutionStatus.RUNNING : ExecutionStatus.SUCCEEDED)
  }

  protected List<String> getInstanceIds(Stage stage) {
    return (List<String>) stage.context.instanceIds
  }

  protected Map getInstance(String account, String region, String instanceId) {
    def response = oortService.getInstance(account, region, instanceId)
    return objectMapper.readValue(response.body.in().text, Map)
  }

  abstract boolean hasSucceeded(List<Map> healthProviders);
}
