/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiGroup
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesKindSpec extends Specification {
  @Unroll
  void "creates built-in API kinds by name"() {
    when:
    def kind = KubernetesKind.fromString(name)

    then:
    kind.equals(expectedKind)

    where:
    name                    | expectedKind
    ""                      | KubernetesKind.NONE
    "replicaSet"            | KubernetesKind.REPLICA_SET
    "replicaSet.extensions" | KubernetesKind.REPLICA_SET
    "replicaSet.apps"       | KubernetesKind.REPLICA_SET
    "networkPolicy"         | KubernetesKind.NETWORK_POLICY
    "NETWORKPOLICY"         | KubernetesKind.NETWORK_POLICY
    "networkpolicy"         | KubernetesKind.NETWORK_POLICY
  }

  @Unroll
  void "kinds are serialized using the Spinnaker-canonical form"() {
    when:
    def kind = KubernetesKind.fromString(name)

    then:
    kind.toString().equals("replicaSet")

    where:
    name << [
      "replicaSet",
      "replicaset",
      "ReplicaSet",
      "REPLICASET",
    ]
  }

  @Unroll
  void "kinds from core API groups are returned if any core API group is input"() {
    when:
    def kind = KubernetesKind.from(name, apiGroup)

    then:
    result == kind

    where:
    name         | apiGroup                      | result
    "replicaSet" | null                          | KubernetesKind.REPLICA_SET
    "replicaSet" | KubernetesApiGroup.APPS       | KubernetesKind.REPLICA_SET
    "replicaSet" | KubernetesApiGroup.EXTENSIONS | KubernetesKind.REPLICA_SET
    "rs"         | null                          | KubernetesKind.REPLICA_SET
    "rs"         | KubernetesApiGroup.APPS       | KubernetesKind.REPLICA_SET

  }

  void "kinds from custom API groups do not return core Kubernetes kinds"() {
    when:
    def kind = KubernetesKind.from("replicaSet", KubernetesApiGroup.fromString("custom"))

    then:
    kind != KubernetesKind.REPLICA_SET
  }

  @Unroll
  void "kinds from core API groups are equal regardless of group"() {
    when:
    def kind1 = KubernetesKind.fromString(name1)
    def kind2 = KubernetesKind.fromString(name2)

    then:
    kind1.equals(kind2) == shouldEqual

    where:
    name1                   | name2                   | shouldEqual
    "deployment.extensions" | "deployment"            | true
    "deployment"            | "deployment.extensions" | true
    "deployment"            | "deployment"            | true
    "replicaSet.extensions" | "replicaSet.apps"       | true
    "replicaSet.apps"       | "replicaSet.extensions" | true
    "replicaSet.apps"       | "deployment.extensions" | false
    "replicaSet"            | "deployment.extensions" | false
    "replicaSet.apps"       | "deployment"            | false
    "replicaSet"            | "deployment"            | false
  }

  @Unroll
  void "kinds from custom API groups are not equal unless the group is equal"() {
    when:
    def kind1 = KubernetesKind.fromString(name1)
    def kind2 = KubernetesKind.fromString(name2)

    then:
    kind1.equals(kind2) == shouldEqual

    where:
    name1                           | name2                           | shouldEqual
    "deployment.stable.example.com" | "deployment.stable.example.com" | true
    "deployment.stable.example.com" | "deployment"                    | false
    "deployment"                    | "deployment.stable.example.com" | false
    "deployment.stable.example.com" | "something.stable.example.com"  | false
    "deployment.stable.example.com" | "deployment.other.example.com"  | false
    "deployment.extensions"         | "deployment.stable.example.com" | false
  }

  void "kinds from core API groups are serialized without the group name"() {
    when:
    def kind = KubernetesKind.fromString(name)
    def string = kind.toString()

    then:
    string == expectedString

    where:
    name                    | expectedString
    "replicaSet"            | "replicaSet"
    "replicaSet.apps"       | "replicaSet"
    "deployment.extensions" | "deployment"
  }

  void "kinds from custom API groups are serialized with the group name"() {
    when:
    def kind = KubernetesKind.fromString(name)
    def string = kind.toString()

    then:
    string == expectedString

    where:
    name                            | expectedString
    "deployment.stable.example.com" | "deployment.stable.example.com"
  }
}
