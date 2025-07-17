package com.fsnow.indexanalyzer.cache;

import com.fsnow.indexanalyzer.model.MongoIndex;

import java.util.Collections;
import java.util.List;

/**
 * Represents a cached entry containing indexes and timestamp.
 * This class is immutable and thread-safe.
 */
final class CacheEntry {
    
    private final List<MongoIndex> indexes;
    private final long timestamp;
    
    /**
     * Creates a new cache entry with the current timestamp.
     * 
     * @param indexes The list of indexes to cache
     */
    CacheEntry(List<MongoIndex> indexes) {
        this.indexes = Collections.unmodifiableList(indexes);
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Gets the cached indexes.
     * 
     * @return An unmodifiable list of indexes
     */
    List<MongoIndex> getIndexes() {
        return indexes;
    }
    
    /**
     * Gets the timestamp when this entry was created.
     * 
     * @return The creation timestamp in milliseconds
     */
    long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Checks if this cache entry has expired based on the given TTL.
     * 
     * @param ttlMillis The time-to-live in milliseconds
     * @return true if the entry has expired, false otherwise
     */
    boolean isExpired(long ttlMillis) {
        return System.currentTimeMillis() - timestamp > ttlMillis;
    }
    
    /**
     * Gets the age of this cache entry in milliseconds.
     * 
     * @return The age in milliseconds
     */
    long getAgeMillis() {
        return System.currentTimeMillis() - timestamp;
    }
}