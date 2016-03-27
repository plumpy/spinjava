/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.securitygroup

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesHttpIngressPath
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesIngressRule
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.securitygroup.KubernetesSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.StandardKubernetesAttributeValidator
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.Errors

class UpsertKubernetesSecurityGroupValidator {
  class UpsertKubernetesLoadBalancerAtomicOperationValidator extends DescriptionValidator<KubernetesSecurityGroupDescription> {
    @Autowired
    AccountCredentialsProvider accountCredentialsProvider

    @Override
    void validate(List priorDescriptions, KubernetesSecurityGroupDescription description, Errors errors) {
      def helper = new StandardKubernetesAttributeValidator("upsertKubernetesSecurityGroupDescription", errors)

      if (!helper.validateCredentials(description.account, accountCredentialsProvider)) {
        return
      }

      KubernetesCredentials credentials = (KubernetesCredentials) accountCredentialsProvider.getCredentials(description.account).credentials

      helper.validateName(description.securityGroupName, "securityGroupName")
      helper.validateNamespace(credentials, description.namespace, "namespace")

      if (description.ingress) {
        if (description.ingress.serviceName) {
          helper.validateName(description.ingress.serviceName, "ingress.serviceName")
        }
        if (description.ingress.port) {
          helper.validatePort(description.ingress.port, "ingress.port")
        }
      }

      if (description.rules) {
        description.rules.eachWithIndex { KubernetesIngressRule rule, i ->
          if (rule.host) {
            helper.validateName(rule.host, "rules[$i].host")
          }
          rule.value?.http?.paths?.eachWithIndex{ KubernetesHttpIngressPath path, j ->
            if (path.path) {
              helper.validatePath(path.path, "rules[$i].value.http.paths[$j].path")
            }
          }
        }
      }
    }
  }
}
