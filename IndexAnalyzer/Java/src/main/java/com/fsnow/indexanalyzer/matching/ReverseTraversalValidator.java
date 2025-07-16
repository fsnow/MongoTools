package com.fsnow.indexanalyzer.matching;

import com.fsnow.indexanalyzer.model.IndexField;
import com.fsnow.indexanalyzer.model.SortDirection;
import com.fsnow.indexanalyzer.model.SortField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Validates whether reverse index traversal is possible and beneficial for queries.
 * 
 * Reverse traversal allows using an index in the opposite direction than its natural order.
 * For example, an index {createdAt: -1} can be used for sorting {createdAt: 1} by traversing
 * the index in reverse.
 * 
 * Key constraints:
 * 1. Cannot reverse traverse when the same field has both range query and sort
 * 2. All sort fields must have opposite direction from index for valid reversal
 * 3. Range fields after equality fields prevent efficient reverse traversal
 */
public class ReverseTraversalValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReverseTraversalValidator.class);
    
    /**
     * Checks if reverse traversal is valid for the given query and index.
     * 
     * @param sortFields The sort fields from the query
     * @param rangeFields The range fields from the query
     * @param equalityFields The equality fields from the query
     * @param indexFields The fields in the index
     * @return true if reverse traversal is valid and beneficial
     */
    public boolean canUseReverseTraversal(List<SortField> sortFields, 
                                        Set<String> rangeFields,
                                        Set<String> equalityFields,
                                        List<IndexField> indexFields) {
        
        if (sortFields.isEmpty()) {
            return true; // No sort fields, reverse traversal is irrelevant
        }
        
        // Check for range + sort on same field (critical constraint)
        boolean hasRangeAndSortConflict = sortFields.stream()
                .anyMatch(sort -> rangeFields.contains(sort.getField()));
        
        if (hasRangeAndSortConflict) {
            logger.debug("Reverse traversal blocked: same field has range query and sort");
            return false;
        }
        
        // Validate sort direction compatibility for reverse traversal
        return validateSortDirectionCompatibility(sortFields, indexFields);
    }
    
    /**
     * Checks if the index can support the sort requirements with reverse traversal.
     * For reverse traversal to work, all sort fields must have opposite direction
     * from their corresponding index fields.
     */
    private boolean validateSortDirectionCompatibility(List<SortField> sortFields, 
                                                     List<IndexField> indexFields) {
        
        for (SortField sortField : sortFields) {
            IndexField matchingIndexField = findIndexField(sortField.getField(), indexFields);
            
            if (matchingIndexField == null) {
                logger.debug("Sort field '{}' not found in index", sortField.getField());
                return false;
            }
            
            // For reverse traversal, sort direction should be opposite to index direction
            SortDirection expectedDirection = sortField.getDirection();
            SortDirection indexDirection = matchingIndexField.getDirection();
            
            if (expectedDirection == indexDirection) {
                // Same direction - normal traversal, not reverse
                logger.debug("Sort field '{}' has same direction as index - normal traversal", 
                           sortField.getField());
                return false;
            }
        }
        
        logger.debug("All sort fields compatible with reverse traversal");
        return true;
    }
    
    /**
     * Checks if all sort fields can be satisfied by reverse traversal.
     * This is more lenient than the strict compatibility check.
     */
    public boolean canSatisfySortWithReverseTraversal(List<SortField> sortFields, 
                                                    List<IndexField> indexFields) {
        
        if (sortFields.isEmpty()) {
            return true;
        }
        
        // Check if we can reverse all sort directions consistently
        Boolean expectingReverse = null;
        
        for (SortField sortField : sortFields) {
            IndexField indexField = findIndexField(sortField.getField(), indexFields);
            
            if (indexField == null) {
                return false; // Sort field not in index
            }
            
            SortDirection sortDirection = sortField.getDirection();
            SortDirection indexDirection = indexField.getDirection();
            
            boolean needsReverse = (sortDirection != indexDirection);
            
            if (expectingReverse == null) {
                expectingReverse = needsReverse;
            } else if (expectingReverse != needsReverse) {
                // Inconsistent reverse requirements
                logger.debug("Inconsistent reverse traversal requirements for sort fields");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Determines if using reverse traversal would be beneficial.
     * Even if reverse traversal is possible, it might not always be the best choice.
     */
    public boolean isReverseTraversalBeneficial(List<SortField> sortFields, 
                                              Set<String> rangeFields,
                                              List<IndexField> indexFields) {
        
        // Reverse traversal is beneficial when:
        // 1. It allows perfect sort order match
        // 2. No range fields conflict with traversal direction
        // 3. The alternative would be in-memory sorting
        
        if (sortFields.isEmpty()) {
            return false; // No benefit without sort requirements
        }
        
        // If range and sort on same field, reverse traversal is not beneficial
        boolean hasRangeAndSortConflict = sortFields.stream()
                .anyMatch(sort -> rangeFields.contains(sort.getField()));
        
        if (hasRangeAndSortConflict) {
            return false;
        }
        
        // Check if reverse traversal would provide perfect sort match
        return canSatisfySortWithReverseTraversal(sortFields, indexFields);
    }
    
    /**
     * Finds an index field by name.
     */
    private IndexField findIndexField(String fieldName, List<IndexField> indexFields) {
        return indexFields.stream()
                .filter(field -> field.getField().equals(fieldName))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Validates complex ESR constraints with reverse traversal considerations.
     * This encompasses the full ESR validation logic with reverse traversal support.
     */
    public ESRValidationResult validateESRWithReverseTraversal(Set<String> equalityFields,
                                                             List<SortField> sortFields,
                                                             Set<String> rangeFields,
                                                             List<IndexField> indexFields) {
        
        // Track which fields we've seen in the index
        int indexPosition = 0;
        boolean inEqualityPhase = true;
        boolean inSortPhase = false;
        boolean inRangePhase = false;
        
        // Process equality fields first (must be prefix of index)
        for (String equalityField : equalityFields) {
            if (indexPosition >= indexFields.size()) {
                return ESRValidationResult.failure("Equality field '" + equalityField + 
                        "' extends beyond index length");
            }
            
            IndexField currentIndexField = indexFields.get(indexPosition);
            if (!currentIndexField.getField().equals(equalityField)) {
                return ESRValidationResult.failure("Equality field '" + equalityField + 
                        "' not at expected index position " + indexPosition);
            }
            
            indexPosition++;
        }
        
        // Process sort fields (must come after equality, before or mixed with range)
        for (SortField sortField : sortFields) {
            if (indexPosition >= indexFields.size()) {
                return ESRValidationResult.failure("Sort field '" + sortField.getField() + 
                        "' extends beyond index length");
            }
            
            IndexField currentIndexField = indexFields.get(indexPosition);
            if (!currentIndexField.getField().equals(sortField.getField())) {
                return ESRValidationResult.failure("Sort field '" + sortField.getField() + 
                        "' not at expected index position " + indexPosition);
            }
            
            // Validate sort direction (consider reverse traversal)
            boolean canSortWithIndex = validateSortDirection(sortField, currentIndexField);
            boolean canSortWithReverse = validateSortDirection(sortField, currentIndexField, true);
            
            if (!canSortWithIndex && !canSortWithReverse) {
                return ESRValidationResult.failure("Sort field '" + sortField.getField() + 
                        "' direction incompatible with index");
            }
            
            inSortPhase = true;
            inEqualityPhase = false;
            indexPosition++;
        }
        
        // Process range fields (must come after equality and sort)
        for (String rangeField : rangeFields) {
            if (sortFields.stream().anyMatch(sort -> sort.getField().equals(rangeField))) {
                // Range and sort on same field - special validation
                continue; // Already validated above
            }
            
            if (indexPosition >= indexFields.size()) {
                return ESRValidationResult.failure("Range field '" + rangeField + 
                        "' extends beyond index length");
            }
            
            IndexField currentIndexField = indexFields.get(indexPosition);
            if (!currentIndexField.getField().equals(rangeField)) {
                return ESRValidationResult.failure("Range field '" + rangeField + 
                        "' not at expected index position " + indexPosition);
            }
            
            inRangePhase = true;
            indexPosition++;
        }
        
        // Determine if reverse traversal is needed and beneficial
        boolean needsReverseTraversal = !canSatisfySortWithNormalTraversal(sortFields, indexFields);
        boolean canUseReverse = canUseReverseTraversal(sortFields, rangeFields, equalityFields, indexFields);
        
        if (needsReverseTraversal && !canUseReverse) {
            return ESRValidationResult.failure("Requires reverse traversal but reverse traversal is not valid");
        }
        
        return ESRValidationResult.success(needsReverseTraversal);
    }
    
    private boolean canSatisfySortWithNormalTraversal(List<SortField> sortFields, 
                                                    List<IndexField> indexFields) {
        for (SortField sortField : sortFields) {
            IndexField indexField = findIndexField(sortField.getField(), indexFields);
            if (indexField == null) {
                return false;
            }
            
            if (!validateSortDirection(sortField, indexField)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean validateSortDirection(SortField sortField, IndexField indexField) {
        return validateSortDirection(sortField, indexField, false);
    }
    
    private boolean validateSortDirection(SortField sortField, IndexField indexField, boolean reverse) {
        SortDirection sortDirection = sortField.getDirection();
        SortDirection indexDirection = indexField.getDirection();
        
        if (reverse) {
            // For reverse traversal, directions should be opposite
            return sortDirection != indexDirection;
        } else {
            // For normal traversal, directions should match
            return sortDirection == indexDirection;
        }
    }
    
    /**
     * Result of ESR validation with reverse traversal considerations.
     */
    public static class ESRValidationResult {
        private final boolean valid;
        private final boolean needsReverseTraversal;
        private final String errorMessage;
        
        private ESRValidationResult(boolean valid, boolean needsReverseTraversal, String errorMessage) {
            this.valid = valid;
            this.needsReverseTraversal = needsReverseTraversal;
            this.errorMessage = errorMessage;
        }
        
        public static ESRValidationResult success(boolean needsReverseTraversal) {
            return new ESRValidationResult(true, needsReverseTraversal, null);
        }
        
        public static ESRValidationResult failure(String errorMessage) {
            return new ESRValidationResult(false, false, errorMessage);
        }
        
        public boolean isValid() { return valid; }
        public boolean needsReverseTraversal() { return needsReverseTraversal; }
        public String getErrorMessage() { return errorMessage; }
    }
}