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

import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.OperationResult
import com.netflix.astyanax.connectionpool.exceptions.BadRequestException
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.model.ColumnList
import com.netflix.astyanax.model.Row
import com.netflix.astyanax.serializers.IntegerSerializer
import com.netflix.astyanax.serializers.MapSerializer
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.spinnaker.front50.exception.NotFoundException
import groovy.util.logging.Slf4j
import org.apache.cassandra.db.marshal.UTF8Type
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component

@Slf4j
@Component
@ConditionalOnExpression('${spinnaker.cassandra.enabled:false}')
class CassandraApplicationDAO implements ApplicationDAO, ApplicationListener<ContextRefreshedEvent> {
  private static final MapSerializer<String, String> mapSerializer = new MapSerializer<String, String>(UTF8Type.instance, UTF8Type.instance)
  private static final Set<String> BUILT_IN_FIELDS = ["name", "description", "email", "updatets", "createts"]

  private static final String CF_NAME = 'application'
  private static final String TEST_QUERY = '''select * from application;'''
  private static ColumnFamily<Integer, String> CF_APPLICATION = ColumnFamily.newColumnFamily(
      CF_NAME, IntegerSerializer.get(), StringSerializer.get()
  )

  @Autowired
  Keyspace keyspace

  @Override
  void onApplicationEvent(ContextRefreshedEvent event) {
    try {
      runQuery(TEST_QUERY)
    } catch (BadRequestException ignored) {
      keyspace.prepareQuery(CF_APPLICATION).withCql(
          '''CREATE TABLE application(
                name varchar,
                description varchar,
                email varchar,
                updatets varchar,
                createts varchar,
                details map<text,text>,
                PRIMARY KEY (name)
             ) with compression={};''').execute()
    }
  }

  @Override
  Application findByName(String name) throws NotFoundException {
    def applications = unmarshallResults(runQuery("select * from application where name='${name}';"))

    if (!applications) {
      throw new NotFoundException("No Application found by name of ${name}")
    }

    return applications[0]
  }

  @Override
  Set<Application> all() {
    def applications = unmarshallResults(runQuery('SELECT * FROM application;'))
    if (!applications) {
      throw new NotFoundException("No applications available")
    }
    return applications
  }

  @Override
  Application create(String id, Map<String, String> attributes) {
    if (!attributes.createTs) {
      // support the migration use-case where we don't want to arbitrarily reset an applications createTs.
      attributes.createTs = System.currentTimeMillis() as String
    }

    runQuery(buildInsertQuery(attributes))
    return new Application(attributes)
  }

  @Override
  void update(String id, Map<String, String> attributes) {
    attributes.name = id
    attributes.updateTs = System.currentTimeMillis() as String
    runQuery(buildInsertQuery(attributes))
  }

  @Override
  void delete(String id) {
    runQuery("DELETE FROM application WHERE name = '${id}';")
  }

  @Override
  boolean isHealthly() {
    try {
      runQuery(TEST_QUERY)
      return true
    } catch (BadRequestException ignored) {
      return false
    }
  }

  @Override
  Set<Application> search(Map<String, String> attributes) {
    // filtering vs. querying to achieve case-insensitivity without using an additional column (small data set)
    def items = all().findAll { app ->
      def result = true
      attributes.each { k, v ->
        if (app.hasProperty(k) && (!app[k] || ((String) app[k]).toLowerCase() != v?.toLowerCase())) {
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

  void truncate() {
    keyspace.truncateColumnFamily(CF_NAME)
  }

  private List<Application> unmarshallResults(OperationResult result) {
    return result.result.rows.collect { Row<Integer, String> row ->
      ColumnList columns = row.columns

      def getStringValue = { String columnName ->
        def column = columns.getColumnByName(columnName)
        return column.hasValue() ? (column.stringValue ?: null) : null
      }

      def detailsColumn = columns.getColumnByName("details")
      def details = [:]
      if (detailsColumn?.hasValue()) {
        details.putAll(detailsColumn.getValue(mapSerializer))
      }

      return new Application(
          name: getStringValue('name'),
          description: getStringValue('description'),
          email: getStringValue('email'),
          owner: details.owner,
          type: details.type,
          group: details.group,
          monitorBucketType: details.monitorBucketType,
          pdApiKey: details.pdApiKey,
          tags: details.tags,
          regions: details.regions,
          updateTs: getStringValue('updatets'),
          createTs: getStringValue('createts'),
      )
    }
  }

  private OperationResult runQuery(String query) {
    return keyspace.prepareQuery(CF_APPLICATION).withCql(query).execute()
  }

  private OperationResult runQuery(Query query) {
    def preparedQuery = keyspace.prepareQuery(CF_APPLICATION).withCql(query.cql).asPreparedStatement()
    query.values.each {
      if (it instanceof String) {
        preparedQuery = preparedQuery.withStringValue(it)
      } else if (it instanceof Map) {
        preparedQuery = preparedQuery.withValue(mapSerializer.toByteBuffer(it))
      }
    }

    return preparedQuery.execute()
  }

  private static Query buildInsertQuery(Map<String, String> attributes) {
    def fields = attributes.keySet().findAll { BUILT_IN_FIELDS.contains(it.toLowerCase()) }
    def values = fields.collect { attributes[it] ?: '' } as List<Object>

    def detailFields = attributes.keySet() - fields
    if (detailFields) {
      fields << "details"
      values << attributes.subMap(detailFields)
    }

    def cql = """INSERT INTO application (${fields.join(",")}) VALUES (${fields.collect { "?" }.join(",")});"""
    return new Query(cql: cql, values: values)
  }

  private static class Query {
    String cql
    List<Object> values
  }
}