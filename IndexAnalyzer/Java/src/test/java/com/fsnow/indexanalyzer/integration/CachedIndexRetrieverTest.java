package com.fsnow.indexanalyzer.integration;

import com.fsnow.indexanalyzer.cache.IndexCache;
import com.fsnow.indexanalyzer.cache.TTLIndexCache;
import com.fsnow.indexanalyzer.model.IndexField;
import com.fsnow.indexanalyzer.model.MongoIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CachedIndexRetrieverTest {
    
    private TestIndexRetriever testDelegate;
    private IndexCache cache;
    private CachedIndexRetriever cachedRetriever;
    private static final String NAMESPACE = "testdb.testcollection";
    
    // Test implementation that tracks method calls
    private static class TestIndexRetriever extends IndexRetriever {
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final List<MongoIndex> indexesToReturn;
        
        public TestIndexRetriever(List<MongoIndex> indexesToReturn) {
            super(null); // Pass null since we're overriding the method
            this.indexesToReturn = indexesToReturn;
        }
        
        @Override
        public List<MongoIndex> getIndexes(String namespace) {
            callCount.incrementAndGet();
            return indexesToReturn;
        }
        
        public int getCallCount() {
            return callCount.get();
        }
        
        public void resetCallCount() {
            callCount.set(0);
        }
    }
    
    @BeforeEach
    void setUp() {
        testDelegate = new TestIndexRetriever(createTestIndexes());
        cache = new TTLIndexCache(5); // 5 minute TTL
        cachedRetriever = new CachedIndexRetriever(testDelegate, cache);
    }
    
    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.shutdown();
        }
    }
    
    @Test
    void testCacheHit() {
        List<MongoIndex> indexes = createTestIndexes();
        
        // First call - should delegate to underlying retriever
        List<MongoIndex> result1 = cachedRetriever.getIndexes(NAMESPACE);
        assertEquals(indexes, result1);
        assertEquals(1, testDelegate.getCallCount());
        
        // Second call - should come from cache
        List<MongoIndex> result2 = cachedRetriever.getIndexes(NAMESPACE);
        assertEquals(indexes, result2);
        
        // Verify delegate was only called once
        assertEquals(1, testDelegate.getCallCount());
    }
    
    @Test
    void testCacheMiss() {
        List<MongoIndex> indexes = createTestIndexes();
        
        // Call with a namespace that's not cached
        List<MongoIndex> result = cachedRetriever.getIndexes(NAMESPACE);
        
        assertEquals(indexes, result);
        assertEquals(1, testDelegate.getCallCount());
        
        // Verify it was cached
        assertEquals(1, cachedRetriever.getCacheSize());
    }
    
    @Test
    void testInvalidateCache() {
        List<MongoIndex> indexes = createTestIndexes();
        
        // Populate cache
        cachedRetriever.getIndexes(NAMESPACE);
        assertEquals(1, cachedRetriever.getCacheSize());
        
        // Invalidate specific namespace
        cachedRetriever.invalidateCache(NAMESPACE);
        assertEquals(0, cachedRetriever.getCacheSize());
        
        // Next call should hit the delegate again
        cachedRetriever.getIndexes(NAMESPACE);
        assertEquals(2, testDelegate.getCallCount());
    }
    
    @Test
    void testInvalidateAllCache() {
        // Populate cache with multiple namespaces
        cachedRetriever.getIndexes("db1.coll1");
        cachedRetriever.getIndexes("db2.coll2");
        assertEquals(2, cachedRetriever.getCacheSize());
        
        // Invalidate all
        cachedRetriever.invalidateAllCache();
        assertEquals(0, cachedRetriever.getCacheSize());
    }
    
    @Test
    void testNullConstructorParameters() {
        assertThrows(IllegalArgumentException.class, () -> new CachedIndexRetriever(null, cache));
        assertThrows(IllegalArgumentException.class, () -> new CachedIndexRetriever(testDelegate, null));
    }
    
    @Test
    void testMultipleNamespaces() {
        // Retrieve different namespaces
        List<MongoIndex> result1 = cachedRetriever.getIndexes("db1.coll1");
        List<MongoIndex> result2 = cachedRetriever.getIndexes("db2.coll2");
        
        // Both should return the same test indexes (from our test implementation)
        assertEquals(createTestIndexes(), result1);
        assertEquals(createTestIndexes(), result2);
        
        // Both should be cached
        assertEquals(2, cachedRetriever.getCacheSize());
        
        // Verify delegate was called twice (once for each namespace)
        assertEquals(2, testDelegate.getCallCount());
    }
    
    private List<MongoIndex> createTestIndexes() {
        return Arrays.asList(
            new MongoIndex("_id_", Arrays.asList(new IndexField("_id", 1))),
            new MongoIndex("status_1_createdAt_-1", Arrays.asList(
                new IndexField("status", 1),
                new IndexField("createdAt", -1)
            ))
        );
    }
}