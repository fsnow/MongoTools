package com.fsnow.indexanalyzer.cache;

import com.fsnow.indexanalyzer.model.MongoIndex;

import java.util.List;
import java.util.Optional;

/**
 * Interface for caching MongoDB index information.
 * Implementations should be thread-safe.
 */
public interface IndexCache {
    
    /**
     * Retrieves cached indexes for a namespace if available and not expired.
     * 
     * @param namespace The database.collection namespace
     * @return Optional containing the cached indexes, or empty if not cached or expired
     */
    Optional<List<MongoIndex>> get(String namespace);
    
    /**
     * Stores indexes in the cache for a namespace.
     * 
     * @param namespace The database.collection namespace
     * @param indexes The list of indexes to cache
     */
    void put(String namespace, List<MongoIndex> indexes);
    
    /**
     * Invalidates cached indexes for a specific namespace.
     * 
     * @param namespace The database.collection namespace
     */
    void invalidate(String namespace);
    
    /**
     * Invalidates all cached indexes.
     */
    void invalidateAll();
    
    /**
     * Gets the current size of the cache.
     * 
     * @return The number of cached namespaces
     */
    int size();
    
    /**
     * Shuts down any background tasks associated with the cache.
     */
    void shutdown();
}