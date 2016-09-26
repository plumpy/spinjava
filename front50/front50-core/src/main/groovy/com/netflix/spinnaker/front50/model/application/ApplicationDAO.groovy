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
import com.netflix.spinnaker.front50.model.ItemDAO

public interface ApplicationDAO extends ItemDAO<Application> {
  Application findByName(String name) throws NotFoundException

  Collection<Application> search(Map<String, String> attributes)

  Collection<Application> getApplicationHistory(String name, int limit)

  static class Searcher {
    static Collection<Application> search(Collection<Application> searchableApplications,
                                          Map<String, String> attributes) {
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

      return items.sort { Application a, Application b ->
        return score(b, attributes) - score(a, attributes)
      }
    }

    static int score(Application application, Map<String, String> attributes) {
      return attributes.collect { key, value ->
        return score(application, key, value)
      }?.sum() as Integer ?: 0
    }

    static int score(Application application, String attributeName, String attributeValue) {
      if (!application.hasProperty(attributeName)) {
        return 0
      }

      def attribute = application[attributeName].toString().toLowerCase()
      def indexOf = attribute.indexOf(attributeValue.toLowerCase())

      // what percentage of the value matched
      def coverage = ((double) attributeValue.length() / attribute.length()) * 100

      // where did the match occur, bonus points for it occurring close to the start
      def boost = attribute.length() - indexOf

      // scale boost based on coverage percentage
      def scaledBoost = ((double) coverage / 100) * boost

      return indexOf < 0 ? 0 : coverage + scaledBoost
    }
  }
}
