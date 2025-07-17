package com.fsnow.indexanalyzer.cache;

import com.fsnow.indexanalyzer.model.MongoIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Time-based cache implementation for MongoDB indexes.
 * Entries expire after a configurable TTL (time-to-live).
 * This implementation is thread-safe.
 */
public class TTLIndexCache implements IndexCache {
    
    private static final Logger logger = LoggerFactory.getLogger(TTLIndexCache.class);
    
    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final long ttlMillis;
    private final ScheduledExecutorService cleanupExecutor;
    
    // Metrics
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    
    /**
     * Creates a new TTL-based cache with the specified time-to-live.
     * 
     * @param ttlMinutes The time-to-live in minutes
     */
    public TTLIndexCache(int ttlMinutes) {
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        
        this.cache = new ConcurrentHashMap<>();
        this.ttlMillis = TimeUnit.MINUTES.toMillis(ttlMinutes);
        
        // Schedule cleanup task to run every minute
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "IndexCache-Cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        cleanupExecutor.scheduleWithFixedDelay(
            this::cleanupExpiredEntries,
            1, 1, TimeUnit.MINUTES
        );
        
        logger.info("TTL Index Cache initialized with TTL={} minutes", 
                   ttlMinutes);
    }
    
    @Override
    public Optional<List<MongoIndex>> get(String namespace) {
        CacheEntry entry = cache.get(namespace);
        
        if (entry == null) {
            missCount.incrementAndGet();
            logger.debug("Cache miss for namespace: {}", namespace);
            return Optional.empty();
        }
        
        if (entry.isExpired(ttlMillis)) {
            cache.remove(namespace);
            missCount.incrementAndGet();
            logger.debug("Cache expired for namespace: {} (age: {} ms)", 
                        namespace, entry.getAgeMillis());
            return Optional.empty();
        }
        
        hitCount.incrementAndGet();
        logger.debug("Cache hit for namespace: {} (age: {} ms)", 
                    namespace, entry.getAgeMillis());
        return Optional.of(entry.getIndexes());
    }
    
    @Override
    public void put(String namespace, List<MongoIndex> indexes) {
        if (namespace == null || indexes == null) {
            throw new IllegalArgumentException("Namespace and indexes cannot be null");
        }
        
        CacheEntry entry = new CacheEntry(indexes);
        cache.put(namespace, entry);
        logger.debug("Cached {} indexes for namespace: {}", indexes.size(), namespace);
    }
    
    @Override
    public void invalidate(String namespace) {
        if (cache.remove(namespace) != null) {
            logger.debug("Invalidated cache for namespace: {}", namespace);
        }
    }
    
    @Override
    public void invalidateAll() {
        int size = cache.size();
        cache.clear();
        logger.info("Invalidated all {} cached entries", size);
    }
    
    @Override
    public int size() {
        return cache.size();
    }
    
    @Override
    public void shutdown() {
        logger.info("Shutting down TTL Index Cache");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Removes expired entries from the cache.
     * This method is called periodically by the cleanup executor.
     */
    private void cleanupExpiredEntries() {
        try {
            int removedCount = 0;
            for (var entry : cache.entrySet()) {
                if (entry.getValue().isExpired(ttlMillis)) {
                    if (cache.remove(entry.getKey(), entry.getValue())) {
                        removedCount++;
                    }
                }
            }
            
            if (removedCount > 0) {
                logger.debug("Cleaned up {} expired cache entries", removedCount);
            }
        } catch (Exception e) {
            logger.error("Error during cache cleanup", e);
        }
    }
    
    /**
     * Gets cache statistics.
     * 
     * @return A string containing cache statistics
     */
    public String getStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        
        return String.format("Cache Stats: size=%d, hits=%d, misses=%d, hitRate=%.1f%%",
                           cache.size(), hits, misses, hitRate);
    }
}