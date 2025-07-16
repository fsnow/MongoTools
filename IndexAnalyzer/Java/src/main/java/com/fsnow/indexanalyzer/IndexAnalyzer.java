package com.fsnow.indexanalyzer;

import com.fsnow.indexanalyzer.exception.InvalidNamespaceException;
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
        this.mongoClientAdapter = new MongoClientAdapter(connectionString, timeoutMs);
        this.indexRetriever = new IndexRetriever(mongoClientAdapter);
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
    
    @Override
    public void close() {
        if (mongoClientAdapter != null) {
            mongoClientAdapter.close();
        }
    }
}