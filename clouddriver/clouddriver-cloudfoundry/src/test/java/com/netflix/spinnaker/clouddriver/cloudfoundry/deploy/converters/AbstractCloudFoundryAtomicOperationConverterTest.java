/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

class AbstractCloudFoundryAtomicOperationConverterTest {
  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
  private final DestroyCloudFoundryServerGroupAtomicOperationConverter converter = new DestroyCloudFoundryServerGroupAtomicOperationConverter();

  {
    when(cloudFoundryClient.getOrganizations().findByName(any()))
      .thenAnswer((Answer<Optional<CloudFoundryOrganization>>) invocation -> {
        Object[] args = invocation.getArguments();
        return Optional.of(CloudFoundryOrganization.builder()
          .id(args[0].toString() + "ID").name(args[0].toString()).build());
      });
  }

  @Test
  void expectFindSpaceSucceeds() {
    when(cloudFoundryClient.getSpaces().findByName(any(), any())).thenAnswer((Answer<CloudFoundrySpace>) invocation -> {
      Object[] args = invocation.getArguments();
      return CloudFoundrySpace.builder().id(args[1].toString() + "ID").name(args[1].toString())
        .organization(CloudFoundryOrganization.builder()
          .id(args[0].toString()).name(args[0].toString().replace("ID", "")).build()).build();
    });

    CloudFoundryOrganization expectedOrg = CloudFoundryOrganization.builder().id("whateverID").name("whatever").build();
    CloudFoundrySpace expectedSpace = CloudFoundrySpace.builder().id("spaceID").name("space").organization(expectedOrg).build();
    assertThat(converter.findSpace("whatever > space", cloudFoundryClient)).isEqualTo(Optional.of(expectedSpace));
  }

  @Test
  void expectExceptionForOrgSpaceNameCaseMismatch() {
    when(cloudFoundryClient.getSpaces().findByName(any(), any())).thenAnswer((Answer<CloudFoundrySpace>) invocation -> {
      Object[] args = invocation.getArguments();
      return CloudFoundrySpace.builder().id(args[1].toString() + "ID").name(args[1].toString())
        .organization(CloudFoundryOrganization.builder()
          .id(args[0].toString()).name(args[0].toString().replace("ID", "").toUpperCase()).build()).build();
    });
    assertThrows(CloudFoundryApiException.class, () -> converter.findSpace("whatever > space", cloudFoundryClient));
  }
}
