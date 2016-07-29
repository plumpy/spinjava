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


package com.netflix.spinnaker.front50.model;

import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import rx.Scheduler;

import java.util.Collection;
import java.util.Map;

public class ApplicationBucketDAO extends BucketDAO<Application> implements ApplicationDAO {
   public ApplicationBucketDAO(String basePath,
                               StorageService service,
                               Scheduler scheduler,
                               int refreshIntervalMs) {
       super(Application.class, "applications",
             basePath, service, scheduler, refreshIntervalMs);
  }

  @Override
  public Application findByName(String name) throws NotFoundException {
      return findById(name);
  }

  @Override
  public Application create(String id, Application application) {
      if (application.getCreateTs() == null) {
          application.setCreateTs(String.valueOf(System.currentTimeMillis()));
      }

      update(id, application);
      return findById(id);
    }

  @Override
  public void update(String id, Application application) {
      application.setName(id);
      super.update(id, application);
  }

  @Override
  public Collection<Application> search(Map<String, String> attributes) {
      return Searcher.search(all(), attributes);
  }

  @Override
  public Collection<Application> getApplicationHistory(String name, int maxResults) {
    return allVersionsOf(name, maxResults);
  }
}
