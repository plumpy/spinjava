/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.ops;

import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.elasticsearch.EntityRefIdBuilder;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import retrofit.RetrofitError;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

public class UpsertEntityTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENTITY_TAGS";

  private final Front50Service front50Service;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;
  private final UpsertEntityTagsDescription entityTagsDescription;

  public UpsertEntityTagsAtomicOperation(Front50Service front50Service,
                                         AccountCredentialsProvider accountCredentialsProvider,
                                         ElasticSearchEntityTagsProvider entityTagsProvider,
                                         UpsertEntityTagsDescription tagEntityDescription) {
    this.front50Service = front50Service;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.entityTagsProvider = entityTagsProvider;
    this.entityTagsDescription = tagEntityDescription;
  }

  public Void operate(List priorOutputs) {
    if (entityTagsDescription.getId() == null) {
      EntityRefIdBuilder.EntityRefId entityRefId = entityRefId(accountCredentialsProvider, entityTagsDescription);

      entityTagsDescription.setId(entityRefId.id);
      entityTagsDescription.setIdPattern(entityRefId.idPattern);
    }

    Date now = new Date();

    if (entityTagsDescription.isPartial) {
      getTask().updateStatus(BASE_PHASE, format("Retrieving %s for partial update", entityTagsDescription.getId()));
      EntityTags currentTags;
      try {
        currentTags = front50Service.getEntityTags(entityTagsDescription.getId());
      } catch (RetrofitError e) {
        getTask().updateStatus(
          BASE_PHASE,
          format("Could not load %s for partial update, will create new tag", entityTagsDescription.getId())
        );
        currentTags = null;
      }
      mergeExistingTagsAndMetadata(now, currentTags, entityTagsDescription);
    } else {
      addTagMetadata(now, entityTagsDescription);
    }

    getTask().updateStatus(
      BASE_PHASE,
      format("Tagging %s with %s", entityTagsDescription.getId(), entityTagsDescription.getTags())
    );

    EntityTags durableEntityTags = front50Service.saveEntityTags(entityTagsDescription);
    getTask().updateStatus(BASE_PHASE, format("Tagged %s in Front50", durableEntityTags.getId()));

    entityTagsDescription.setLastModified(durableEntityTags.getLastModified());
    entityTagsDescription.setLastModifiedBy(durableEntityTags.getLastModifiedBy());

    entityTagsProvider.index(entityTagsDescription);
    entityTagsProvider.verifyIndex(entityTagsDescription);

    getTask().updateStatus(BASE_PHASE, format("Indexed %s in ElasticSearch", entityTagsDescription.getId()));
    return null;
  }

  private static EntityRefIdBuilder.EntityRefId entityRefId(AccountCredentialsProvider accountCredentialsProvider,
                                                            UpsertEntityTagsDescription description) {
    EntityTags.EntityRef entityRef = description.getEntityRef();
    String entityRefAccount = (String) entityRef.attributes().get("account");
    String entityRefAccountId = (String) entityRef.attributes().get("accountId");

    if (entityRefAccount != null && entityRefAccountId == null) {
      // add `accountId` if available (and not already specified)
      AccountCredentials accountCredentials = accountCredentialsProvider.getCredentials(entityRefAccount);
      if (accountCredentials != null) {
        entityRefAccountId = accountCredentials.getAccountId();
        entityRef.attributes().put("accountId", entityRefAccountId);
      }
    }

    return EntityRefIdBuilder.buildId(
      entityRef.getCloudProvider(),
      entityRef.getEntityType(),
      entityRef.getEntityId(),
      Optional.ofNullable(entityRefAccountId).orElse(entityRefAccount),
      (String) entityRef.attributes().get("region")
    );
  }

  private static void mergeExistingTagsAndMetadata(Date now,
                                                   EntityTags currentTags,
                                                   EntityTags updatedTags) {
    String user = AuthenticatedRequest.getSpinnakerUser().orElse("unknown");

    if (currentTags == null) {
      addTagMetadata(now, updatedTags);
      return;
    }

    updatedTags.setTagsMetadata(
      currentTags.getTagsMetadata() == null ? new HashMap<>() : currentTags.getTagsMetadata()
    );

    updatedTags.getTags().forEach((key, tag) -> {
      EntityTags.EntityTagMetadata metadata = currentTags.getTagsMetadata().get(key);
      if (metadata == null) {
        metadata = new EntityTags.EntityTagMetadata();
        metadata.setCreated(now.getTime());
        metadata.setCreatedBy(user);
        updatedTags.getTagsMetadata().put(key, metadata);
      }
      metadata.setLastModified(now.getTime());
      metadata.setLastModifiedBy(user);
    });

    currentTags.getTags().forEach((key, tag) -> {
      if (!updatedTags.getTags().containsKey(key)) {
        updatedTags.getTags().put(key, tag);
        updatedTags.getTagsMetadata().put(key, currentTags.getTagsMetadata().get(key));
      }
    });
  }

  private static EntityTags.EntityTagMetadata tagMetadata(Date now) {
    String user = AuthenticatedRequest.getSpinnakerUser().orElse("unknown");

    EntityTags.EntityTagMetadata metadata = new EntityTags.EntityTagMetadata();
    metadata.setCreated(now.getTime());
    metadata.setLastModified(now.getTime());
    metadata.setCreatedBy(user);
    metadata.setLastModifiedBy(user);

    return metadata;
  }

  private static void addTagMetadata(Date now, EntityTags entityTags) {
    entityTags.setTagsMetadata(new HashMap<>());
    entityTags.getTags().forEach((key, tag) -> entityTags.getTagsMetadata().put(key, tagMetadata(now)));
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
