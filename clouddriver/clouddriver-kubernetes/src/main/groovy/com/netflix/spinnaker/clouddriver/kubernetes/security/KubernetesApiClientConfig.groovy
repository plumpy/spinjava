/*
 * Copyright 2017 Cisco, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.SSLUtils;

import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import javax.net.ssl.KeyManager;

@Slf4j
public class KubernetesApiClientConfig extends Config {
  String kubeconfigFile
  String context
  String cluster
  String user
  String userAgent

  public KubernetesApiClientConfig(String kubeconfigFile, String context, String cluster, String user, String userAgent) {
    this.kubeconfigFile = kubeconfigFile
    this.context = context
    this.user = user
    this.userAgent = userAgent
  }

  public ApiClient getApiCient() throws Exception {
    KubeConfig kubeconfig

    try {
      if (StringUtils.isEmpty(kubeconfigFile)) {
        kubeconfig = KubeConfig.loadDefaultKubeConfig()
      } else {
        kubeconfig = KubeConfig.loadKubeConfig(new FileReader(kubeconfigFile))
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Unable to create credentials from kubeconfig file: " + e, e)
    } catch (Exception e2) {
      throw new RuntimeException("Missing required field(s) in kubenetes configuration file.")
    }

    InputStream is = new FileInputStream(kubeconfigFile)
    Reader input = new InputStreamReader(is)
    Yaml yaml = new Yaml(new SafeConstructor())
    Object config = yaml.load(input)
    Map<String, Object> configMap = (Map<String, Object>)config

    //TODO: Need to validate cluster and user when client library exposes these api.
    if (StringUtils.isEmpty(context) && !configMap.get("current-context")) {
      throw new RuntimeException("Missing required field ${context} in kubeconfig file and clouddriver configuration.")
    }

    if (!StringUtils.isEmpty(context)) {
      kubeconfig.setContext(context);
    }

    ApiClient client = Config.fromConfig(kubeconfig);

    if (!StringUtils.isEmpty(userAgent)) {
      client.setUserAgent(userAgent);
    }

    is.close()
    input.close()

    return client
  }
}
