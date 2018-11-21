package com.netflix.spinnaker.cats.sql

import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.cats.sql.cache.SqlCache
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*
import org.slf4j.LoggerFactory

class SqlProviderCache(private val backingStore: WriteableCache) : ProviderCache {

  private val ALL_ID = "_ALL_" // this implementation ignores this entirely
  private val log = LoggerFactory.getLogger(javaClass)

  init {
    if (backingStore !is SqlCache) {
      throw IllegalStateException("SqlProviderCache must be wired with a SqlCache backingStore")
    }
  }

  /**
   * Filters the supplied list of identifiers to only those that exist in the cache.
   *
   * @param type the type of the item
   * @param identifiers the identifiers for the items
   * @return the list of identifiers that are present in the cache from the provided identifiers
   */
  override fun existingIdentifiers(type: String?, identifiers: MutableCollection<String>?): MutableCollection<String>? {
    log.debug("existingIdentifiers not implemented in ${this.javaClass}, should only be passe existing ids")
    return identifiers
  }

  /**
   * Returns the identifiers for the specified type that match the provided glob.
   *
   * @param type The type for which to retrieve identifiers
   * @param glob The glob to match against the identifiers
   * @return the identifiers for the type that match the glob
   */
  override fun filterIdentifiers(type: String?, glob: String?): MutableCollection<String> {
    return backingStore.filterIdentifiers(type, glob)
  }

  /**
   * Retrieves all the items for the specified type
   *
   * @param type the type for which to retrieve items
   * @return all the items for the type
   */
  override fun getAll(type: String): MutableCollection<CacheData> {
    return getAll(type, null as CacheFilter?)
  }

  override fun getAll(type: String, identifiers: MutableCollection<String>?): MutableCollection<CacheData> {
    return getAll(type, identifiers, null)
  }

  override fun getAll(type: String, cacheFilter: CacheFilter?): MutableCollection<CacheData> {
    validateTypes(type)
    return backingStore.getAll(type, cacheFilter)
  }

  override fun getAll(type: String, identifiers: MutableCollection<String>?, cacheFilter: CacheFilter?): MutableCollection<CacheData> {
    validateTypes(type)
    return backingStore.getAll(type, identifiers, cacheFilter)
  }

  /**
   * Retrieves the items for the specified type matching the provided identifiers
   *
   * @param type        the type for which to retrieve items
   * @param identifiers the identifiers
   * @return the items matching the type and identifiers
   */
  override fun getAll(type: String, vararg identifiers: String): MutableCollection<CacheData> {
    return getAll(type, identifiers.toMutableList())
  }

  /**
   * Gets a single item from the cache by type and id
   *
   * @param type the type of the item
   * @param id   the id of the item
   * @return the item matching the type and id
   */
  override fun get(type: String, id: String?): CacheData? {
    return get(type, id, null)
  }

  override fun get(type: String, id: String?, cacheFilter: CacheFilter?): CacheData? {
    if (ALL_ID == id) {
      log.warn("Unexpected request for $ALL_ID for type: $type, cacheFilter: $cacheFilter")
      return null
    }
    validateTypes(type)

    return backingStore.get(type, id, cacheFilter) ?: return null
  }

  override fun evictDeletedItems(type: String, ids: Collection<String>) {
    backingStore.evictAll(type, ids)
  }

  /**
   * Retrieves all the identifiers for a type
   *
   * @param type the type for which to retrieve identifiers
   * @return the identifiers for the type
   */
  override fun getIdentifiers(type: String): MutableCollection<String> {
    validateTypes(type)
    return backingStore.getIdentifiers(type)
  }

  override fun putCacheResult(source: String, authoritativeTypes: MutableCollection<String>?, cacheResult: CacheResult?) {
    if (cacheResult == null) {
      return
    }

    val cachedTypes = mutableSetOf<String>()
    // Update resource table from Authoritative sources only
    if (authoritativeTypes != null && authoritativeTypes.isNotEmpty()) {
      cacheResult.cacheResults
        .filter { authoritativeTypes.contains(it.key) }
        .forEach {
          cacheDataType(it.key, source, it.value, true)
          cachedTypes.add(it.key)
        }
    }

    // Update relationships for non-authoritative types
    cacheResult.cacheResults
      .filter { !cachedTypes.contains(it.key) }
      .forEach {
        cacheDataType(it.key, source, it.value, false)
      }
  }

  override fun putCacheData(type: String, cacheData: CacheData) {
    backingStore.merge(type, cacheData)
  }

  private fun validateTypes(type: String) {
    validateTypes(listOf(type))
  }

  private fun validateTypes(types: List<String>) {
    val invalid = types
      .asSequence()
      .filter { it.contains(":") }
      .toSet()

    if (invalid.isNotEmpty()) {
      throw IllegalArgumentException("Invalid types: $invalid")
    }
  }

  private fun cacheDataType(type: String, agent: String, items: Collection<CacheData>, authoritative: Boolean) {
    val toStore = ArrayList<CacheData>(items.size + 1)
    items.forEach {
      toStore.add(uniqueifyRelationships(it, agent))
    }

    // TODO terrible hack because no AWS agent is authoritative for clusters, fix in ClusterCachingAgent
    // TODO same with namedImages - fix in AWS ImageCachingAgent
    val override =
      if (
        (type == CLUSTERS.toString() && agent.contains("clustercaching", ignoreCase = true)) ||
        (type == NAMED_IMAGES.toString() && agent.contains("imagecaching", ignoreCase = true))
      ) {
        true
      } else {
        authoritative
      }

    (backingStore as SqlCache).mergeAll(type, agent, toStore, override, true)
  }

  private fun uniqueifyRelationships(source: CacheData, sourceAgentType: String): CacheData {
    val relationships = HashMap<String, Collection<String>>(source.relationships.size)
    for ((key, value) in source.relationships) {
      relationships[key + ':'.toString() + sourceAgentType] = value
    }
    return DefaultCacheData(source.id, source.ttlSeconds, source.attributes, relationships)
  }
}
