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



package com.netflix.spinnaker.front50.model.application

import com.netflix.spinnaker.front50.exception.NotFoundException

public interface ApplicationDAO extends com.netflix.spinnaker.front50.model.ItemDAO<Application> {
  Application findByName(String name) throws NotFoundException

  Collection<Application> search(Map<String, String> attributes)

  Collection<Application> getApplicationHistory(String name, int maxResults)

  static class Searcher {
    static Collection<Application> search(Collection<Application> searchableApplications, Map<String, String> attributes) {
      attributes = attributes.collect { k,v -> [k.toLowerCase(), v] }.collectEntries()

      if (attributes["accounts"]) {
        def accounts = attributes["accounts"].split(",").collect { it.trim().toLowerCase() }
        searchableApplications = searchableApplications.findAll {
          def applicationAccounts = (it.accounts ?: "").split(",").collect { it.trim().toLowerCase() }
          return applicationAccounts.containsAll(accounts)
        }

        // remove the 'accounts' search attribute so it's not picked up again in the field-level filtering below
        attributes.remove("accounts")
      }

      // filtering vs. querying to achieve case-insensitivity without using an additional column (small data set)
      def items = searchableApplications.findAll { app ->
        def result = true
        attributes.each { k, v ->
          if (!v) {
            return
          }
          if (!app.hasProperty(k) && !app.details().containsKey(k)) {
            result = false
          }
          def appVal = app.hasProperty(k) ? app[k] : app.details()[k] ?: ""
          if (!appVal.toString().toLowerCase().contains(v.toLowerCase())) {
            result = false
          }
        }
        return result
      } as Set

      if (!items) {
        throw new NotFoundException("No Application found for search criteria $attributes")
      }

      return items
    }
  }
}
