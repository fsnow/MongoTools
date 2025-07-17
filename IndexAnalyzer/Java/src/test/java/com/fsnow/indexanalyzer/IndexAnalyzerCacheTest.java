package com.fsnow.indexanalyzer;

import com.fsnow.indexanalyzer.config.IndexAnalyzerConfig;
import com.fsnow.indexanalyzer.integration.CachedIndexRetriever;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IndexAnalyzerCacheTest {
    
    private IndexAnalyzer analyzer;
    
    @AfterEach
    void tearDown() {
        if (analyzer != null) {
            analyzer.close();
        }
    }
    
    @Test
    void testCachingEnabled() {
        // Create analyzer with caching enabled
        IndexAnalyzerConfig config = IndexAnalyzerConfig.builder()
                .cacheEnabled(true)
                .cacheTTLMinutes(5)
                .build();
        
        analyzer = new IndexAnalyzer("mongodb://localhost:27017", config);
        
        // Verify caching is enabled
        Optional<CachedIndexRetriever> cachedRetriever = analyzer.getCachedIndexRetriever();
        assertTrue(cachedRetriever.isPresent());
        
        // Check cache stats - should show cache disabled initially
        String stats = analyzer.getCacheStats();
        assertFalse(stats.equals("Caching disabled"));
    }
    
    @Test
    void testCachingDisabled() {
        // Create analyzer with default config (caching disabled)
        analyzer = new IndexAnalyzer("mongodb://localhost:27017");
        
        // Verify caching is disabled
        Optional<CachedIndexRetriever> cachedRetriever = analyzer.getCachedIndexRetriever();
        assertFalse(cachedRetriever.isPresent());
        
        // Cache stats should indicate disabled
        assertEquals("Caching disabled", analyzer.getCacheStats());
    }
    
    @Test
    void testCacheInvalidation() {
        IndexAnalyzerConfig config = IndexAnalyzerConfig.builder()
                .cacheEnabled(true)
                .cacheTTLMinutes(5)
                .build();
        
        analyzer = new IndexAnalyzer("mongodb://localhost:27017", config);
        
        Optional<CachedIndexRetriever> cachedRetriever = analyzer.getCachedIndexRetriever();
        assertTrue(cachedRetriever.isPresent());
        
        // Verify cache starts empty
        assertEquals(0, cachedRetriever.get().getCacheSize());
        
        // Test cache invalidation methods
        cachedRetriever.get().invalidateCache("testdb.testcollection");
        cachedRetriever.get().invalidateAllCache();
        
        // Cache should still be empty
        assertEquals(0, cachedRetriever.get().getCacheSize());
    }
    
    @Test
    void testConfigBuilder() {
        IndexAnalyzerConfig config = IndexAnalyzerConfig.builder()
                .cacheEnabled(true)
                .cacheTTLMinutes(10)
                .connectionTimeoutMs(60000)
                .build();
        
        assertTrue(config.isCacheEnabled());
        assertEquals(10, config.getCacheTTLMinutes());
        assertEquals(60000, config.getConnectionTimeoutMs());
        
        // Test toString
        String configStr = config.toString();
        assertTrue(configStr.contains("cacheEnabled=true"));
        assertTrue(configStr.contains("cacheTTLMinutes=10"));
    }
}