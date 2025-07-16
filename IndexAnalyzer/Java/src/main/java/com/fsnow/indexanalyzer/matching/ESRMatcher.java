package com.fsnow.indexanalyzer.matching;

import com.fsnow.indexanalyzer.model.IndexField;
import com.fsnow.indexanalyzer.model.MongoIndex;
import com.fsnow.indexanalyzer.model.QueryAnalysis;
import com.fsnow.indexanalyzer.model.SortField;
import com.fsnow.indexanalyzer.model.SortDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements ESR (Equality-Sort-Range) pattern matching for MongoDB indexes.
 * 
 * ESR principle:
 * - Equality fields first (order doesn't matter within equality)
 * - Sort fields next (exact order and direction must match, with reverse traversal support)
 * - Range fields last (must be present somewhere in index)
 * 
 * Phase 3 enhancement: Supports reverse index traversal for sort optimization.
 */
public class ESRMatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(ESRMatcher.class);
    private final ReverseTraversalValidator reverseValidator;
    
    public ESRMatcher() {
        this.reverseValidator = new ReverseTraversalValidator();
    }
    
    /**
     * Determines if an index perfectly matches a query using ESR principles.
     */
    public boolean indexPerfectlyMatches(QueryAnalysis queryAnalysis, 
                                         List<SortField> sortFields, 
                                         MongoIndex index) {
        List<IndexField> indexFields = index.getFields();
        
        logger.debug("Checking index {} against query analysis", index.getName());
        
        // Step 1: Match all equality fields (order doesn't matter within equality group)
        Set<String> equalityFields = new HashSet<>(queryAnalysis.getEqualityFields());
        Set<String> unmatchedEquality = new HashSet<>(equalityFields);
        
        // For ESR to be valid, ALL equality fields must come first in the index
        // Find the longest prefix of equality fields
        int equalityEndPosition = 0;
        for (int i = 0; i < indexFields.size(); i++) {
            String currentField = indexFields.get(i).getField();
            if (equalityFields.contains(currentField)) {
                unmatchedEquality.remove(currentField);
                equalityEndPosition = i + 1;
            } else {
                // If we encounter a non-equality field, stop looking for equality fields
                // All remaining equality fields must come after this position, which violates ESR
                break;
            }
        }
        
        // If not all equality fields are covered in the prefix, this violates ESR
        if (!unmatchedEquality.isEmpty()) {
            logger.debug("Index {} missing equality fields in prefix: {}", 
                    index.getName(), unmatchedEquality);
            return false;
        }
        
        logger.debug("Index {} covers all equality fields in positions 0-{}", 
                index.getName(), equalityEndPosition - 1);
        
        // Step 2: Match sort fields with reverse traversal support (Phase 3 enhancement)
        int sortEndPosition = equalityEndPosition;
        if (!sortFields.isEmpty()) {
            List<IndexField> remainingIndexFields = new ArrayList<>();
            for (int i = equalityEndPosition; i < indexFields.size(); i++) {
                remainingIndexFields.add(indexFields.get(i));
            }
            
            if (remainingIndexFields.size() < sortFields.size()) {
                logger.debug("Index {} has insufficient remaining fields for sort", index.getName());
                return false;
            }
            
            // Use comprehensive ESR validation with reverse traversal
            ReverseTraversalValidator.ESRValidationResult validation = 
                    reverseValidator.validateESRWithReverseTraversal(
                            equalityFields, sortFields, new HashSet<>(queryAnalysis.getRangeFields()), indexFields);
            
            if (!validation.isValid()) {
                logger.debug("Index {} ESR validation failed: {}", index.getName(), validation.getErrorMessage());
                return false;
            }
            
            if (validation.needsReverseTraversal()) {
                logger.debug("Index {} requires reverse traversal for sort optimization", index.getName());
            }
            
            sortEndPosition = equalityEndPosition + sortFields.size();
            logger.debug("Index {} matches sort fields in positions {}-{} (reverse: {})", 
                    index.getName(), equalityEndPosition, sortEndPosition - 1, validation.needsReverseTraversal());
        }
        
        // Step 3: Check range fields
        // Range fields must appear somewhere in the index
        Set<String> rangeFields = new HashSet<>(queryAnalysis.getRangeFields());
        Set<String> coveredFields = new HashSet<>();
        
        // Add all fields from the index
        for (IndexField indexField : indexFields) {
            coveredFields.add(indexField.getField());
        }
        
        // Check if all range fields are covered somewhere in the index
        for (String rangeField : rangeFields) {
            if (!coveredFields.contains(rangeField)) {
                logger.debug("Index {} missing range field: {}", index.getName(), rangeField);
                return false;
            }
        }
        
        logger.debug("Index {} perfectly matches query using ESR pattern", index.getName());
        return true;
    }
    
    /**
     * Enhanced matching that provides detailed information about reverse traversal requirements.
     * This method returns additional information useful for query optimization.
     */
    public ESRMatchResult indexMatchesWithDetails(QueryAnalysis queryAnalysis, 
                                                 List<SortField> sortFields, 
                                                 MongoIndex index) {
        
        Set<String> equalityFields = new HashSet<>(queryAnalysis.getEqualityFields());
        Set<String> rangeFields = new HashSet<>(queryAnalysis.getRangeFields());
        List<IndexField> indexFields = index.getFields();
        
        // Use the comprehensive ESR validation
        ReverseTraversalValidator.ESRValidationResult validation = 
                reverseValidator.validateESRWithReverseTraversal(
                        equalityFields, sortFields, rangeFields, indexFields);
        
        if (!validation.isValid()) {
            return ESRMatchResult.noMatch(validation.getErrorMessage());
        }
        
        // Check if all required fields are covered
        Set<String> allQueryFields = new HashSet<>();
        allQueryFields.addAll(equalityFields);
        allQueryFields.addAll(rangeFields);
        sortFields.forEach(sort -> allQueryFields.add(sort.getField()));
        
        Set<String> indexFieldNames = new HashSet<>();
        indexFields.forEach(field -> indexFieldNames.add(field.getField()));
        
        for (String queryField : allQueryFields) {
            if (!indexFieldNames.contains(queryField)) {
                return ESRMatchResult.noMatch("Missing field: " + queryField);
            }
        }
        
        return ESRMatchResult.perfectMatch(validation.needsReverseTraversal());
    }
    
    /**
     * Result of detailed ESR matching analysis.
     */
    public static class ESRMatchResult {
        private final boolean matches;
        private final boolean needsReverseTraversal;
        private final String reason;
        
        private ESRMatchResult(boolean matches, boolean needsReverseTraversal, String reason) {
            this.matches = matches;
            this.needsReverseTraversal = needsReverseTraversal;
            this.reason = reason;
        }
        
        public static ESRMatchResult perfectMatch(boolean needsReverseTraversal) {
            return new ESRMatchResult(true, needsReverseTraversal, "Perfect ESR match");
        }
        
        public static ESRMatchResult noMatch(String reason) {
            return new ESRMatchResult(false, false, reason);
        }
        
        public boolean matches() { return matches; }
        public boolean needsReverseTraversal() { return needsReverseTraversal; }
        public String getReason() { return reason; }
    }
}