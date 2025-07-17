package com.fsnow.indexanalyzer.integration;

import com.fsnow.indexanalyzer.cache.IndexCache;
import com.fsnow.indexanalyzer.model.MongoIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Decorator for IndexRetriever that adds caching functionality.
 * This class implements a read-through cache pattern.
 */
public class CachedIndexRetriever extends IndexRetriever {
    
    private static final Logger logger = LoggerFactory.getLogger(CachedIndexRetriever.class);
    
    private final IndexRetriever delegate;
    private final IndexCache cache;
    
    /**
     * Creates a new cached index retriever.
     * 
     * @param delegate The underlying IndexRetriever to delegate to
     * @param cache The cache implementation to use
     */
    public CachedIndexRetriever(IndexRetriever delegate, IndexCache cache) {
        // Pass null to parent since we're using delegation
        super(null);
        
        if (delegate == null || cache == null) {
            throw new IllegalArgumentException("Delegate and cache cannot be null");
        }
        
        this.delegate = delegate;
        this.cache = cache;
    }
    
    /**
     * Retrieves indexes from cache if available, otherwise fetches from MongoDB.
     * Implements a read-through cache pattern.
     * 
     * @param namespace The database.collection namespace
     * @return List of indexes for the namespace
     */
    @Override
    public List<MongoIndex> getIndexes(String namespace) {
        logger.debug("Retrieving indexes for namespace: {}", namespace);
        
        // Try to get from cache first
        Optional<List<MongoIndex>> cachedIndexes = cache.get(namespace);
        if (cachedIndexes.isPresent()) {
            logger.debug("Returning cached indexes for namespace: {}", namespace);
            return cachedIndexes.get();
        }
        
        // Cache miss - fetch from MongoDB
        logger.debug("Cache miss for namespace: {}, fetching from MongoDB", namespace);
        List<MongoIndex> indexes = delegate.getIndexes(namespace);
        
        // Store in cache for future use
        cache.put(namespace, indexes);
        
        return indexes;
    }
    
    /**
     * Invalidates the cache for a specific namespace.
     * This can be called when indexes are known to have changed.
     * 
     * @param namespace The namespace to invalidate
     */
    public void invalidateCache(String namespace) {
        cache.invalidate(namespace);
        logger.info("Invalidated cache for namespace: {}", namespace);
    }
    
    /**
     * Invalidates the entire cache.
     * This can be called when a full refresh is needed.
     */
    public void invalidateAllCache() {
        cache.invalidateAll();
        logger.info("Invalidated entire index cache");
    }
    
    /**
     * Gets the current cache size.
     * 
     * @return The number of cached namespaces
     */
    public int getCacheSize() {
        return cache.size();
    }
}