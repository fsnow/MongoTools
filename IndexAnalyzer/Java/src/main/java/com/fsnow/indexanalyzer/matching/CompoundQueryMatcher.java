package com.fsnow.indexanalyzer.matching;

import com.fsnow.indexanalyzer.model.IndexField;
import com.fsnow.indexanalyzer.model.MongoIndex;
import com.fsnow.indexanalyzer.model.QueryAnalysis;
import com.fsnow.indexanalyzer.model.SortField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enhanced query matching that handles compound sort fields and complex query scenarios.
 * 
 * Phase 3 enhancement: Focuses on perfect index coverage for complete queries including:
 * - Multi-field sort requirements
 * - OR queries (all branches must be perfectly covered)
 * - Complex ESR patterns with reverse traversal
 * 
 * Returns boolean results: either the query is perfectly supported or it isn't.
 */
public class CompoundQueryMatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(CompoundQueryMatcher.class);
    private final ESRMatcher esrMatcher;
    private final ReverseTraversalValidator reverseValidator;
    
    public CompoundQueryMatcher() {
        this.esrMatcher = new ESRMatcher();
        this.reverseValidator = new ReverseTraversalValidator();
    }
    
    /**
     * Determines if the given indexes can perfectly support the complete query including sort.
     * For OR queries, ALL branches must be perfectly supported.
     * 
     * @param queryAnalysis The analyzed query (may contain OR branches)
     * @param sortFields The sort requirements
     * @param indexes Available indexes
     * @return true if query is perfectly supported, false otherwise
     */
    public boolean hasIndexPerfectMatch(QueryAnalysis queryAnalysis, 
                                      List<SortField> sortFields, 
                                      List<MongoIndex> indexes) {
        
        if (queryAnalysis.hasOr()) {
            // OR query: ALL branches must be perfectly supported
            return allOrBranchesSupported(queryAnalysis.getOrBranches(), sortFields, indexes);
        } else {
            // Simple query: check if any index perfectly supports it
            return anyIndexSupportsQuery(queryAnalysis, sortFields, indexes);
        }
    }
    
    /**
     * Checks if all OR branches can be perfectly supported by the available indexes.
     */
    private boolean allOrBranchesSupported(List<QueryAnalysis> orBranches, 
                                         List<SortField> sortFields, 
                                         List<MongoIndex> indexes) {
        
        for (QueryAnalysis branch : orBranches) {
            if (!anyIndexSupportsQuery(branch, sortFields, indexes)) {
                logger.debug("OR branch not supported: {}", branch);
                return false;
            }
        }
        
        logger.debug("All {} OR branches are perfectly supported", orBranches.size());
        return true;
    }
    
    /**
     * Checks if any available index can perfectly support the query and sort.
     */
    private boolean anyIndexSupportsQuery(QueryAnalysis queryAnalysis, 
                                        List<SortField> sortFields, 
                                        List<MongoIndex> indexes) {
        
        for (MongoIndex index : indexes) {
            if (indexPerfectlySupportsQuery(queryAnalysis, sortFields, index)) {
                logger.debug("Query perfectly supported by index: {}", index.getName());
                return true;
            }
        }
        
        logger.debug("No index can perfectly support this query");
        return false;
    }
    
    /**
     * Determines if a single index can perfectly support the query and sort requirements.
     * This is the core logic that combines ESR matching with compound sort support.
     */
    private boolean indexPerfectlySupportsQuery(QueryAnalysis queryAnalysis, 
                                               List<SortField> sortFields, 
                                               MongoIndex index) {
        
        Set<String> equalityFields = queryAnalysis.getEqualityFields();
        Set<String> rangeFields = queryAnalysis.getRangeFields();
        List<IndexField> indexFields = index.getFields();
        
        // Step 1: Verify all query fields are covered by the index
        if (!allQueryFieldsCovered(equalityFields, rangeFields, sortFields, indexFields)) {
            return false;
        }
        
        // Step 2: Verify ESR pattern compliance with compound sort support
        return esrPatternSupportsCompoundSort(equalityFields, rangeFields, sortFields, indexFields);
    }
    
    /**
     * Checks if all query fields (equality, range, and sort) are present in the index.
     */
    private boolean allQueryFieldsCovered(Set<String> equalityFields, 
                                        Set<String> rangeFields, 
                                        List<SortField> sortFields, 
                                        List<IndexField> indexFields) {
        
        Set<String> indexFieldNames = new HashSet<>();
        indexFields.forEach(field -> indexFieldNames.add(field.getField()));
        
        // Check equality fields coverage
        for (String field : equalityFields) {
            if (!indexFieldNames.contains(field)) {
                logger.debug("Equality field '{}' not covered by index", field);
                return false;
            }
        }
        
        // Check range fields coverage
        for (String field : rangeFields) {
            if (!indexFieldNames.contains(field)) {
                logger.debug("Range field '{}' not covered by index", field);
                return false;
            }
        }
        
        // Check sort fields coverage
        for (SortField sortField : sortFields) {
            if (!indexFieldNames.contains(sortField.getField())) {
                logger.debug("Sort field '{}' not covered by index", sortField.getField());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Verifies that the index fields follow ESR pattern and can support compound sort.
     * This is the critical logic that determines perfect index coverage.
     */
    private boolean esrPatternSupportsCompoundSort(Set<String> equalityFields, 
                                                  Set<String> rangeFields, 
                                                  List<SortField> sortFields, 
                                                  List<IndexField> indexFields) {
        
        // For simple cases (no sort fields), use the basic ESR matcher for compatibility
        if (sortFields.isEmpty()) {
            // Create a temporary query analysis and use the basic ESR matcher
            QueryAnalysis tempAnalysis = QueryAnalysis.simple(equalityFields, rangeFields);
            MongoIndex tempIndex = new MongoIndex("temp", indexFields);
            return esrMatcher.indexPerfectlyMatches(tempAnalysis, sortFields, tempIndex);
        }
        
        // For complex cases with sort fields, use comprehensive ESR validation
        ReverseTraversalValidator.ESRValidationResult validation = 
                reverseValidator.validateESRWithReverseTraversal(
                        equalityFields, sortFields, rangeFields, indexFields);
        
        if (!validation.isValid()) {
            logger.debug("ESR validation failed: {}", validation.getErrorMessage());
            return false;
        }
        
        // Additional validation: ensure perfect compound sort support
        return validateCompoundSortRequirements(equalityFields, sortFields, indexFields, validation);
    }
    
    /**
     * Validates that compound sort requirements are perfectly met.
     */
    private boolean validateCompoundSortRequirements(Set<String> equalityFields, 
                                                    List<SortField> sortFields, 
                                                    List<IndexField> indexFields,
                                                    ReverseTraversalValidator.ESRValidationResult esrValidation) {
        
        if (sortFields.isEmpty()) {
            return true; // No sort requirements
        }
        
        // Find where equality fields end in the index
        int equalityEndPosition = 0;
        for (IndexField indexField : indexFields) {
            if (equalityFields.contains(indexField.getField())) {
                equalityEndPosition++;
            } else {
                break;
            }
        }
        
        // Check if we have enough index fields for all sort fields
        if (equalityEndPosition + sortFields.size() > indexFields.size()) {
            logger.debug("Not enough index fields for compound sort");
            return false;
        }
        
        // Validate each sort field matches the corresponding index field
        for (int i = 0; i < sortFields.size(); i++) {
            SortField sortField = sortFields.get(i);
            IndexField indexField = indexFields.get(equalityEndPosition + i);
            
            // Field names must match
            if (!sortField.getField().equals(indexField.getField())) {
                logger.debug("Sort field '{}' position mismatch in index", sortField.getField());
                return false;
            }
            
            // Direction must be compatible (considering reverse traversal)
            if (!sortDirectionCompatible(sortField, indexField, esrValidation.needsReverseTraversal())) {
                logger.debug("Sort direction incompatible for field '{}' (reverse: {})", 
                           sortField.getField(), esrValidation.needsReverseTraversal());
                return false;
            }
        }
        
        logger.debug("Compound sort perfectly supported with {} fields", sortFields.size());
        return true;
    }
    
    /**
     * Checks if sort direction is compatible considering reverse traversal.
     */
    private boolean sortDirectionCompatible(SortField sortField, IndexField indexField, boolean reverseTraversal) {
        boolean directionsMatch = sortField.getDirection().equals(indexField.getDirection());
        
        if (reverseTraversal) {
            // For reverse traversal, directions should be opposite
            return !directionsMatch;
        } else {
            // For normal traversal, directions should match
            return directionsMatch;
        }
    }
}