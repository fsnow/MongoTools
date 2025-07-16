package com.fsnow.indexanalyzer.parser;

import org.bson.Document;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Analyzes MongoDB operators to determine field types.
 * Phase 3 enhancement: Advanced operator analysis for $elemMatch, $not, etc.
 */
public class OperatorAnalyzer {
    
    private static final Set<String> EQUALITY_OPERATORS = new HashSet<>(Arrays.asList(
            "$eq", "$in", "$ne"
    ));
    
    private static final Set<String> RANGE_OPERATORS = new HashSet<>(Arrays.asList(
            "$gt", "$gte", "$lt", "$lte"
    ));
    
    private static final Set<String> LOGICAL_OPERATORS = new HashSet<>(Arrays.asList(
            "$and", "$or", "$nor"
    ));
    
    private static final Set<String> SPECIAL_OPERATORS = new HashSet<>(Arrays.asList(
            "$not", "$elemMatch", "$exists", "$type", "$regex", "$mod", "$all", "$size"
    ));
    
    /**
     * Determines if an operator represents an equality condition.
     */
    public static boolean isEqualityOperator(String operator) {
        return EQUALITY_OPERATORS.contains(operator);
    }
    
    /**
     * Determines if an operator represents a range condition.
     */
    public static boolean isRangeOperator(String operator) {
        return RANGE_OPERATORS.contains(operator);
    }
    
    /**
     * Determines if an operator is a logical operator.
     */
    public static boolean isLogicalOperator(String operator) {
        return LOGICAL_OPERATORS.contains(operator);
    }
    
    /**
     * Determines if an operator is a special operator that needs custom handling.
     */
    public static boolean isSpecialOperator(String operator) {
        return SPECIAL_OPERATORS.contains(operator);
    }
    
    /**
     * Determines if a value contains any range operators.
     * Used for analyzing complex conditions like $not.
     */
    public static boolean containsRangeOperator(Object value) {
        if (!(value instanceof Document)) {
            return false;
        }
        
        Document doc = (Document) value;
        return doc.keySet().stream().anyMatch(OperatorAnalyzer::isRangeOperator);
    }
    
    /**
     * Analyzes a $not operator to determine the underlying field type.
     * Phase 3 enhancement: More sophisticated $not analysis.
     */
    public static FieldType analyze$NotOperator(Object notValue) {
        if (notValue instanceof Document) {
            Document notDoc = (Document) notValue;
            
            // Check what's inside the $not
            boolean hasRangeOperators = notDoc.keySet().stream()
                    .anyMatch(OperatorAnalyzer::isRangeOperator);
            boolean hasEqualityOperators = notDoc.keySet().stream()
                    .anyMatch(OperatorAnalyzer::isEqualityOperator);
            
            if (hasRangeOperators) {
                // $not with range operators is complex and should be treated as range
                return FieldType.RANGE;
            } else if (hasEqualityOperators) {
                // $not with equality operators can be treated as equality for index purposes
                return FieldType.EQUALITY;
            } else {
                // Complex $not conditions (regex, etc.) - conservative approach
                return FieldType.COMPLEX;
            }
        } else {
            // Simple $not with direct value: { field: { $not: { $eq: "value" } } }
            return FieldType.EQUALITY;
        }
    }
    
    /**
     * Analyzes an $elemMatch operator to extract field information.
     * Phase 3 enhancement: Comprehensive $elemMatch analysis.
     */
    public static ElemMatchAnalysis analyze$ElemMatchOperator(String baseFieldName, Object elemMatchValue) {
        if (!(elemMatchValue instanceof Document)) {
            return ElemMatchAnalysis.simple(baseFieldName);
        }
        
        Document elemMatchDoc = (Document) elemMatchValue;
        Set<String> equalityFields = new HashSet<>();
        Set<String> rangeFields = new HashSet<>();
        Set<String> complexFields = new HashSet<>();
        
        for (String fieldName : elemMatchDoc.keySet()) {
            Object fieldValue = elemMatchDoc.get(fieldName);
            String fullFieldName = baseFieldName + "." + fieldName;
            
            if (fieldValue instanceof Document) {
                Document fieldDoc = (Document) fieldValue;
                
                boolean hasRangeOps = fieldDoc.keySet().stream()
                        .anyMatch(OperatorAnalyzer::isRangeOperator);
                boolean hasEqualityOps = fieldDoc.keySet().stream()
                        .anyMatch(OperatorAnalyzer::isEqualityOperator);
                boolean hasSpecialOps = fieldDoc.keySet().stream()
                        .anyMatch(OperatorAnalyzer::isSpecialOperator);
                
                if (hasRangeOps) {
                    rangeFields.add(fullFieldName);
                } else if (hasEqualityOps) {
                    equalityFields.add(fullFieldName);
                } else if (hasSpecialOps) {
                    complexFields.add(fullFieldName);
                } else {
                    // Unknown operators - treat as complex
                    complexFields.add(fullFieldName);
                }
            } else {
                // Direct value assignment in $elemMatch
                equalityFields.add(fullFieldName);
            }
        }
        
        return new ElemMatchAnalysis(equalityFields, rangeFields, complexFields);
    }
    
    /**
     * Determines if an operator combination suggests complex index requirements.
     * Phase 3 enhancement: Better complex operator detection.
     */
    public static boolean isComplexOperatorCombination(Document operatorDoc) {
        Set<String> operators = operatorDoc.keySet();
        
        // Multiple range operators on same field
        long rangeOpCount = operators.stream()
                .filter(OperatorAnalyzer::isRangeOperator)
                .count();
        
        if (rangeOpCount > 2) {
            return true; // Multiple range constraints might be complex
        }
        
        // Mix of special operators
        boolean hasSpecial = operators.stream()
                .anyMatch(OperatorAnalyzer::isSpecialOperator);
        boolean hasRange = operators.stream()
                .anyMatch(OperatorAnalyzer::isRangeOperator);
        
        if (hasSpecial && hasRange) {
            return true; // Mix of special and range operators
        }
        
        // Specific complex operators
        if (operators.contains("$regex") || operators.contains("$mod") || 
            operators.contains("$where") || operators.contains("$expr")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Analyzes whether an operator can be efficiently supported by B-tree indexes.
     * Phase 3 enhancement: B-tree efficiency analysis.
     */
    public static boolean isBTreeEfficient(String operator, Object value) {
        switch (operator) {
            case "$eq":
            case "$in":
            case "$gt":
            case "$gte":
            case "$lt":
            case "$lte":
                return true; // Standard B-tree operations
                
            case "$ne":
                return false; // $ne requires scanning most of the index
                
            case "$not":
                // Depends on what's inside the $not
                return analyze$NotOperator(value) != FieldType.COMPLEX;
                
            case "$elemMatch":
                // Simple $elemMatch can be efficient with appropriate indexes
                return true;
                
            case "$exists":
                return value.equals(true); // exists: true can use sparse indexes
                
            case "$regex":
            case "$mod":
            case "$where":
            case "$expr":
                return false; // These require document examination
                
            default:
                return false; // Unknown operators - conservative approach
        }
    }
    
    /**
     * Field type classification for advanced analysis.
     */
    public enum FieldType {
        EQUALITY,   // Can be handled efficiently with equality matching
        RANGE,      // Requires range scanning
        COMPLEX     // Requires complex evaluation, may not use indexes efficiently
    }
    
    /**
     * Result of $elemMatch analysis.
     */
    public static class ElemMatchAnalysis {
        private final Set<String> equalityFields;
        private final Set<String> rangeFields;
        private final Set<String> complexFields;
        
        public ElemMatchAnalysis(Set<String> equalityFields, Set<String> rangeFields, Set<String> complexFields) {
            this.equalityFields = equalityFields;
            this.rangeFields = rangeFields;
            this.complexFields = complexFields;
        }
        
        public static ElemMatchAnalysis simple(String baseFieldName) {
            return new ElemMatchAnalysis(
                    Set.of(baseFieldName), 
                    Set.of(), 
                    Set.of()
            );
        }
        
        public Set<String> getEqualityFields() { return equalityFields; }
        public Set<String> getRangeFields() { return rangeFields; }
        public Set<String> getComplexFields() { return complexFields; }
        
        public boolean hasComplexFields() { return !complexFields.isEmpty(); }
        public boolean isSimple() { return complexFields.isEmpty() && rangeFields.isEmpty(); }
    }
}