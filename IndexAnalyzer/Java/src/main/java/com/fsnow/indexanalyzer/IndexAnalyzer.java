package com.fsnow.indexanalyzer;

import com.fsnow.indexanalyzer.cache.IndexCache;
import com.fsnow.indexanalyzer.cache.TTLIndexCache;
import com.fsnow.indexanalyzer.config.IndexAnalyzerConfig;
import com.fsnow.indexanalyzer.exception.InvalidNamespaceException;
import com.fsnow.indexanalyzer.integration.CachedIndexRetriever;
import com.fsnow.indexanalyzer.integration.IndexRetriever;
import com.fsnow.indexanalyzer.integration.MongoClientAdapter;
import com.fsnow.indexanalyzer.matching.IndexMatcher;
import com.fsnow.indexanalyzer.model.MongoIndex;
import com.fsnow.indexanalyzer.model.QueryAnalysis;
import com.fsnow.indexanalyzer.model.SortField;
import com.fsnow.indexanalyzer.parser.CriteriaParser;
import com.fsnow.indexanalyzer.parser.SortParser;
import com.fsnow.indexanalyzer.transformation.DNFTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;

/**
 * Main entry point for MongoDB index coverage analysis.
 * Analyzes if a query can be executed using only B-tree indexes without in-memory operations.
 */
public class IndexAnalyzer implements Closeable {
    
    private static final Logger logger = LoggerFactory.getLogger(IndexAnalyzer.class);
    
    private final MongoClientAdapter mongoClientAdapter;
    private final IndexRetriever indexRetriever;
    private final CriteriaParser criteriaParser;
    private final SortParser sortParser;
    private final DNFTransformer dnfTransformer;
    private final IndexMatcher indexMatcher;
    private final Optional<IndexCache> indexCache;
    private final IndexAnalyzerConfig config;
    
    /**
     * Creates an IndexAnalyzer with a MongoDB connection string.
     */
    public IndexAnalyzer(String connectionString) {
        this(connectionString, 30000); // 30 second default timeout
    }
    
    /**
     * Creates an IndexAnalyzer with a MongoDB connection string and timeout.
     */
    public IndexAnalyzer(String connectionString, int timeoutMs) {
        this(connectionString, IndexAnalyzerConfig.builder()
                .connectionTimeoutMs(timeoutMs)
                .build());
    }
    
    /**
     * Creates an IndexAnalyzer with a MongoDB connection string and configuration.
     * This constructor allows enabling caching and other advanced options.
     * 
     * @param connectionString The MongoDB connection string
     * @param config The configuration for the analyzer
     */
    public IndexAnalyzer(String connectionString, IndexAnalyzerConfig config) {
        this.config = config;
        this.mongoClientAdapter = new MongoClientAdapter(connectionString, config.getConnectionTimeoutMs());
        
        // Create base index retriever
        IndexRetriever baseRetriever = new IndexRetriever(mongoClientAdapter);
        
        // Setup caching if enabled
        if (config.isCacheEnabled()) {
            IndexCache cache = new TTLIndexCache(config.getCacheTTLMinutes());
            this.indexCache = Optional.of(cache);
            this.indexRetriever = new CachedIndexRetriever(baseRetriever, cache);
            logger.info("Index caching enabled with TTL={} minutes", config.getCacheTTLMinutes());
        } else {
            this.indexCache = Optional.empty();
            this.indexRetriever = baseRetriever;
            logger.info("Index caching disabled");
        }
        
        this.criteriaParser = new CriteriaParser();
        this.sortParser = new SortParser();
        this.dnfTransformer = new DNFTransformer();
        this.indexMatcher = new IndexMatcher();
    }
    
    /**
     * Analyzes if a query can use indexes without in-memory operations.
     * 
     * @param criteria The MongoDB query criteria
     * @param sort The sort specification (can be null)
     * @param namespace The database.collection namespace
     * @return true if perfect index match exists, false otherwise
     */
    public boolean analyzeIndexCoverage(Criteria criteria, Sort sort, String namespace) {
        logger.info("Analyzing index coverage for namespace: {}", namespace);
        
        // Validate namespace format
        validateNamespace(namespace);
        
        try {
            // Parse query and sort
            QueryAnalysis queryAnalysis = criteriaParser.parse(criteria);
            List<SortField> sortFields = sortParser.parse(sort);
            
            logger.debug("Query analysis: {}", queryAnalysis);
            logger.debug("Sort fields: {}", sortFields);
            
            // Get indexes for the collection
            List<MongoIndex> indexes = indexRetriever.getIndexes(namespace);
            
            if (indexes.isEmpty()) {
                logger.warn("No indexes found for namespace: {}", namespace);
                return false;
            }
            
            // Check if any index provides perfect match using ESR matching
            boolean hasPerfectMatch = indexMatcher.hasIndexPerfectMatch(queryAnalysis, sortFields, indexes);
            
            logger.info("Index coverage analysis result: {}", hasPerfectMatch);
            return hasPerfectMatch;
            
        } catch (Exception e) {
            logger.error("Error analyzing index coverage", e);
            throw e;
        }
    }
    
    /**
     * Validates that the namespace follows the "database.collection" format.
     * 
     * @param namespace The namespace to validate
     * @throws InvalidNamespaceException if the namespace format is invalid
     */
    private void validateNamespace(String namespace) {
        if (namespace == null || namespace.trim().isEmpty()) {
            throw new InvalidNamespaceException(namespace);
        }
        
        // Check for exactly one dot to separate database and collection
        String[] parts = namespace.split("\\.");
        if (parts.length != 2) {
            throw new InvalidNamespaceException(namespace);
        }
        
        // Check that both database and collection names are non-empty
        String database = parts[0].trim();
        String collection = parts[1].trim();
        if (database.isEmpty() || collection.isEmpty()) {
            throw new InvalidNamespaceException(namespace);
        }
    }
    
    /**
     * Gets the cached index retriever if caching is enabled.
     * This allows access to cache management methods.
     * 
     * @return Optional containing the CachedIndexRetriever if caching is enabled
     */
    public Optional<CachedIndexRetriever> getCachedIndexRetriever() {
        if (indexRetriever instanceof CachedIndexRetriever) {
            return Optional.of((CachedIndexRetriever) indexRetriever);
        }
        return Optional.empty();
    }
    
    /**
     * Gets cache statistics if caching is enabled.
     * 
     * @return Cache statistics string, or "Caching disabled" if not enabled
     */
    public String getCacheStats() {
        if (indexCache.isPresent() && indexCache.get() instanceof TTLIndexCache) {
            return ((TTLIndexCache) indexCache.get()).getStats();
        }
        return "Caching disabled";
    }
    
    @Override
    public void close() {
        // Shutdown cache if present
        indexCache.ifPresent(IndexCache::shutdown);
        
        if (mongoClientAdapter != null) {
            mongoClientAdapter.close();
        }
    }
}