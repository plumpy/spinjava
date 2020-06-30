/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.cats.sql.cache

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.sql.SqlAgent
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.kork.sql.routing.withPool
import java.sql.SQLException
import java.util.concurrent.TimeUnit
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider

/**
 * Intermittently scans the entire database looking for records created by caching agents that
 * are no longer configured.
 */
class SqlUnknownAgentCleanupAgent(
  private val providerRegistry: ObjectProvider<ProviderRegistry>,
  private val jooq: DSLContext,
  private val registry: Registry,
  private val sqlNames: SqlNames
) : RunnableAgent, CustomScheduledAgent, SqlAgent {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val deletedId = registry.createId("sql.cacheCleanupAgent.dataTypeRecordsDeleted")
  private val timingId = registry.createId("sql.cacheCleanupAgent.dataTypeCleanupDuration")

  override fun run() {
    log.info("Scanning for cache records to cleanup")

    val (agentTypes, agentDataTypes) = findAgentDataTypes()
    val runState = RunState(agentTypes)

    val numDataTypes = agentDataTypes.size
    log.info("Found {} cache data types generated from {} agent types", numDataTypes, agentTypes.size)

    var failures = 0
    withPool(ConnectionPools.CACHE_WRITER.value) {
      agentDataTypes.forEachIndexed { i, dataType ->
        log.info("Scanning '$dataType' (${i + 1}/$numDataTypes) cache records to cleanup")
        try {
          registry.timer(timingId.withTag("dataType", dataType)).record {
            cleanTable(CacheTable.RELATIONSHIP, dataType, runState)
            cleanTable(CacheTable.RESOURCE, dataType, runState)
          }
        } catch (e: SQLException) {
          log.error("Failed to cleanup '$dataType'", e)
          failures++
        }
      }
    }

    log.info("Finished cleanup ($failures failures)")
  }

  /**
   * If the table for [dataType] has not been touched yet, scan through each record it contains,
   * deleting all records that do not correlate to a currently configured agent.
   */
  private fun cleanTable(cacheTable: CacheTable, dataType: String, state: RunState) {
    val tableName = cacheTable.getName(sqlNames, dataType)

    if (state.touchedTables.contains(tableName)) {
      // Nothing to do here, we've already processed this table.
      return
    }
    log.debug("Checking table '$tableName' for '$dataType' data cleanup")

    val tableExists = jooq.fetch("show tables like '$tableName'").intoResultSet()
    if (!tableExists.next()) {
      log.debug("Table '$tableName' not found")
      state.touchedTables.add(tableName)
      return
    }

    val rs = jooq.select(*cacheTable.fields)
      .from(table(tableName))
      .fetch()
      .intoResultSet()

    val cleanedAgentTypes = mutableSetOf<String>()
    val idsToClean = mutableListOf<String>()
    while (rs.next()) {
      val agentType = processRelAgentTypeValue(rs.getString(2))
      if (!state.agentTypes.contains(agentType)) {
        idsToClean.add(rs.getString(1))
        cleanedAgentTypes.add(agentType)
      }
    }

    if (idsToClean.isNotEmpty()) {
      log.info(
        "Found ${idsToClean.size} records to cleanup from '$tableName' for data type '$dataType'. " +
          "Reason: Data generated by unknown caching agents ($cleanedAgentTypes})"
      )
      idsToClean.chunked(100) { chunk ->
        jooq.deleteFrom(table(tableName))
          .where(field(cacheTable.idColumn()).`in`(*chunk.toTypedArray()))
          .execute()
      }
    }

    state.touchedTables.add(tableName)

    registry
      .counter(deletedId.withTags("dataType", dataType, "table", cacheTable.name))
      .increment(idsToClean.size.toLong())
  }

  /**
   * The "rel_agent" column value is a little wonky. It uses a format of `{dataType}:{agentName}`, but we only want the
   * agent name, so we'll split on the colon value, removing the first element.
   *
   * TODO(rz): The Eureka health agents are particularly annoying, since they're just named after the HTTP endpoint
   *  they hit. This case is handled specifically, but we should just change the agent name to have better consistency
   *  with other agent names.
   */
  private fun processRelAgentTypeValue(agentType: String): String =
    agentType.split(":").let {
      if (it.size == 1) {
        agentType
      } else {
        // Gross little hack here for Eureka agents.
        if (agentType.startsWith("http://") || agentType.startsWith("https://")) {
          agentType
        } else {
          it.subList(1, it.size).joinToString(":")
        }
      }
    }

  /**
   * Returns a set of all known caching agent names and another set of all known authoritative
   * data types from those caching agents.
   *
   * Agent names will be used to identify what records in the database are no longer attached
   * to existing caching agents, whereas the data types themselves are needed to create the
   * SQL table names, as the tables are derived from the data types, not the agents.
   */
  private fun findAgentDataTypes(): Pair<Set<String>, Set<String>> {
    var result: Pair<Set<String>, Set<String>> = Pair(setOf(), setOf())
    providerRegistry.ifAvailable { registry ->
      val agents = registry.providers
        .flatMap { it.agents }
        .filterIsInstance<CachingAgent>()

      val dataTypes = agents
        .flatMap { it.providedDataTypes }
        .filter { it.authority == AUTHORITATIVE }
        .map { it.typeName }
        .toSet()

      result = Pair(agents.mapNotNull { sqlNames.checkAgentName(it.agentType) }.toSet(), dataTypes)
    }
    return result
  }

  /**
   * Contains per-run state of this cleanup agent.
   */
  private data class RunState(
    val agentTypes: Set<String>,
    val touchedTables: MutableList<String> = mutableListOf()
  )

  /**
   * Abstracts the logical differences--as far as this agent is concerned--between the two
   * varieties of cache tables: The table names and the associated fields we need to read
   * from the database.
   */
  private enum class CacheTable(val fields: Array<Field<*>>) {
    RESOURCE(arrayOf(field("id"), field("agent"))),
    RELATIONSHIP(arrayOf(field("uuid"), field("rel_agent")));

    fun idColumn(): String =
      when (this) {
        RESOURCE -> "id"
        RELATIONSHIP -> "uuid"
      }

    fun getName(sqlNames: SqlNames, dataType: String): String =
      when (this) {
        RESOURCE -> sqlNames.resourceTableName(dataType)
        RELATIONSHIP -> sqlNames.relTableName(dataType)
      }
  }

  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis(): Long = DEFAULT_POLL_INTERVAL
  override fun getTimeoutMillis(): Long = DEFAULT_TIMEOUT
  override fun getAgentType(): String = javaClass.simpleName

  companion object {
    private val DEFAULT_POLL_INTERVAL = TimeUnit.MINUTES.toMillis(2)
    private val DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(1)
  }
}
