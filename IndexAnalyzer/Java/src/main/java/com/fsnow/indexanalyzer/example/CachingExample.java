package com.fsnow.indexanalyzer.example;

import com.fsnow.indexanalyzer.IndexAnalyzer;
import com.fsnow.indexanalyzer.config.IndexAnalyzerConfig;
import com.fsnow.indexanalyzer.integration.CachedIndexRetriever;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Optional;

/**
 * Example demonstrating how to use the IndexAnalyzer with caching enabled.
 * This is particularly useful for applications that frequently analyze queries
 * and need to minimize MongoDB calls for index retrieval.
 */
public class CachingExample {
    
    public static void main(String[] args) {
        String connectionString = "mongodb://localhost:27017";
        String namespace = "myapp.users";
        
        // Create configuration with caching enabled
        IndexAnalyzerConfig config = IndexAnalyzerConfig.builder()
                .cacheEnabled(true)
                .cacheTTLMinutes(5)          // Cache indexes for 5 minutes
                .connectionTimeoutMs(30000)  // 30 second MongoDB timeout
                .build();
        
        // Create analyzer with caching configuration
        try (IndexAnalyzer analyzer = new IndexAnalyzer(connectionString, config)) {
            
            // Example 1: Analyze multiple queries for the same collection
            // The indexes will be cached after the first query
            
            Criteria criteria1 = Criteria.where("status").is("active");
            Sort sort1 = Sort.by(Sort.Direction.DESC, "createdAt");
            
            System.out.println("=== First Query Analysis ===");
            boolean result1 = analyzer.analyzeIndexCoverage(criteria1, sort1, namespace);
            System.out.println("Query 1 covered by index: " + result1);
            System.out.println("Cache stats: " + analyzer.getCacheStats());
            
            // Second query - should use cached indexes
            Criteria criteria2 = Criteria.where("email").is("user@example.com");
            
            System.out.println("\n=== Second Query Analysis ===");
            boolean result2 = analyzer.analyzeIndexCoverage(criteria2, null, namespace);
            System.out.println("Query 2 covered by index: " + result2);
            System.out.println("Cache stats: " + analyzer.getCacheStats());
            
            // Example 2: Cache management
            Optional<CachedIndexRetriever> cachedRetriever = analyzer.getCachedIndexRetriever();
            if (cachedRetriever.isPresent()) {
                System.out.println("\n=== Cache Management ===");
                System.out.println("Current cache size: " + cachedRetriever.get().getCacheSize());
                
                // Invalidate cache for a specific namespace
                cachedRetriever.get().invalidateCache(namespace);
                System.out.println("Cache size after invalidation: " + cachedRetriever.get().getCacheSize());
                
                // Analyze again - will fetch fresh indexes
                System.out.println("\n=== After Cache Invalidation ===");
                boolean result3 = analyzer.analyzeIndexCoverage(criteria1, sort1, namespace);
                System.out.println("Query 3 covered by index: " + result3);
                System.out.println("Cache stats: " + analyzer.getCacheStats());
            }
            
            // Example 3: Different configurations
            System.out.println("\n=== Configuration Examples ===");
            
            // High-frequency application with longer cache TTL
            IndexAnalyzerConfig highFreqConfig = IndexAnalyzerConfig.builder()
                    .cacheEnabled(true)
                    .cacheTTLMinutes(15)        // Cache for 15 minutes
                    .build();
            
            System.out.println("High frequency config: " + highFreqConfig);
            
            // Application with shorter TTL for more frequent refreshes
            IndexAnalyzerConfig shortTTLConfig = IndexAnalyzerConfig.builder()
                    .cacheEnabled(true)
                    .cacheTTLMinutes(2)         // Short TTL for fresher data
                    .build();
            
            System.out.println("Short TTL config: " + shortTTLConfig);
            
            // Disabled caching (default behavior)
            IndexAnalyzerConfig noCacheConfig = IndexAnalyzerConfig.defaultConfig();
            System.out.println("No cache config: " + noCacheConfig);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Example of how to use the analyzer in a service layer
     */
    public static class QueryRoutingService {
        
        private final IndexAnalyzer analyzer;
        
        public QueryRoutingService(String connectionString) {
            // Configure for high-frequency usage
            IndexAnalyzerConfig config = IndexAnalyzerConfig.builder()
                    .cacheEnabled(true)
                    .cacheTTLMinutes(5)
                    .build();
            
            this.analyzer = new IndexAnalyzer(connectionString, config);
        }
        
        public void routeQuery(Criteria criteria, Sort sort, String namespace) {
            // Analyze if query can use MongoDB indexes
            boolean canUseIndexes = analyzer.analyzeIndexCoverage(criteria, sort, namespace);
            
            if (canUseIndexes) {
                System.out.println("Routing to MongoDB for namespace: " + namespace);
                // Execute query against MongoDB
            } else {
                System.out.println("Routing to Atlas Search for namespace: " + namespace);
                // Execute query against Atlas Search
            }
        }
        
        public void shutdown() {
            analyzer.close();
        }
    }
}