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
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.DeleteEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import retrofit.RetrofitError;

import java.util.List;

import static java.lang.String.format;

public class DeleteEntityTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENTITY_TAGS";

  private final Front50Service front50Service;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;
  private final DeleteEntityTagsDescription entityTagsDescription;

  public DeleteEntityTagsAtomicOperation(Front50Service front50Service,
                                         ElasticSearchEntityTagsProvider entityTagsProvider,
                                         DeleteEntityTagsDescription entityTagsDescription) {
    this.front50Service = front50Service;
    this.entityTagsProvider = entityTagsProvider;
    this.entityTagsDescription = entityTagsDescription;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, format("Retrieving %s from Front50", entityTagsDescription.getId()));
    EntityTags currentTags;
    try {
      currentTags = front50Service.getEntityTags(entityTagsDescription.getId());
    } catch (RetrofitError e) {
      getTask().updateStatus(BASE_PHASE, format("Did not find %s in Front50", entityTagsDescription.getId()));
      return null;
    }
    if (entityTagsDescription.isDeleteAll() || entityTagsDescription.getTags().containsAll(currentTags.getTags().keySet())) {
      getTask().updateStatus(BASE_PHASE, format("Deleting %s from ElasticSearch", entityTagsDescription.getId()));
      entityTagsProvider.delete(entityTagsDescription.getId());
      getTask().updateStatus(BASE_PHASE, format("Deleted %s from ElasticSearch", entityTagsDescription.getId()));

      getTask().updateStatus(BASE_PHASE, format("Deleting %s from Front50", entityTagsDescription.getId()));
      front50Service.deleteEntityTags(entityTagsDescription.getId());
      getTask().updateStatus(BASE_PHASE, format("Deleted %s from Front50", entityTagsDescription.getId()));
      return null;
    }

    entityTagsDescription.getTags().forEach(tag -> {
      currentTags.getTags().remove(tag);
      if (currentTags.getTagsMetadata() != null) {
        currentTags.getTagsMetadata().remove(tag);
      }
    });

    EntityTags durableEntityTags = front50Service.saveEntityTags(currentTags);
    getTask().updateStatus(BASE_PHASE, format("Updated %s in Front50", durableEntityTags.getId()));

    currentTags.setLastModified(durableEntityTags.getLastModified());
    currentTags.setLastModifiedBy(durableEntityTags.getLastModifiedBy());

    entityTagsProvider.index(currentTags);
    entityTagsProvider.verifyIndex(currentTags);

    getTask().updateStatus(BASE_PHASE, format("Updated %s in ElasticSearch", entityTagsDescription.getId()));

    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
