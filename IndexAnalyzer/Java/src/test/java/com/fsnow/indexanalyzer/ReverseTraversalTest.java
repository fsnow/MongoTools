package com.fsnow.indexanalyzer;

import com.fsnow.indexanalyzer.matching.ESRMatcher;
import com.fsnow.indexanalyzer.matching.ReverseTraversalValidator;
import com.fsnow.indexanalyzer.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test suite for reverse index traversal functionality (Phase 3).
 * Tests advanced ESR scenarios with reverse traversal optimization.
 */
class ReverseTraversalTest {
    
    private ReverseTraversalValidator reverseValidator;
    private ESRMatcher esrMatcher;
    
    @BeforeEach
    void setUp() {
        reverseValidator = new ReverseTraversalValidator();
        esrMatcher = new ESRMatcher();
    }
    
    @Test
    void testSimpleReverseTraversalValid() {
        // Index: {createdAt: -1}, Query sort: {createdAt: 1}
        // Should allow reverse traversal
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("createdAt", SortDirection.DESC)
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.ASC)
        );
        
        Set<String> rangeFields = new HashSet<>();
        Set<String> equalityFields = new HashSet<>();
        
        boolean canReverse = reverseValidator.canUseReverseTraversal(
                sortFields, rangeFields, equalityFields, indexFields);
        
        assertThat(canReverse).isTrue();
    }
    
    @Test
    void testReverseTraversalBlockedBySameFieldRangeAndSort() {
        // Range and sort on same field should block reverse traversal
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("score", SortDirection.DESC)
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("score", SortDirection.ASC)
        );
        
        Set<String> rangeFields = new HashSet<>();
        rangeFields.add("score"); // Same field as sort
        
        Set<String> equalityFields = new HashSet<>();
        
        boolean canReverse = reverseValidator.canUseReverseTraversal(
                sortFields, rangeFields, equalityFields, indexFields);
        
        assertThat(canReverse).isFalse();
    }
    
    @Test
    void testMultiFieldSortConsistentReversal() {
        // All sort fields need consistent reverse direction
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("status", SortDirection.ASC),
                new IndexField("createdAt", SortDirection.DESC),
                new IndexField("score", SortDirection.ASC)
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("status", SortDirection.DESC),     // opposite to index (needs reverse)
                new SortField("createdAt", SortDirection.ASC),   // opposite to index (needs reverse)
                new SortField("score", SortDirection.DESC)       // opposite to index (needs reverse)
        );
        
        Set<String> rangeFields = new HashSet<>();
        Set<String> equalityFields = new HashSet<>();
        
        boolean canSatisfy = reverseValidator.canSatisfySortWithReverseTraversal(
                sortFields, indexFields);
        
        assertThat(canSatisfy).isTrue();
    }
    
    @Test
    void testMultiFieldSortInconsistentReversal() {
        // Mixed directions should fail
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("status", SortDirection.ASC),
                new IndexField("createdAt", SortDirection.DESC)
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("status", SortDirection.ASC),      // same as index (normal)
                new SortField("createdAt", SortDirection.ASC)    // opposite to index (reverse)
        );
        
        boolean canSatisfy = reverseValidator.canSatisfySortWithReverseTraversal(
                sortFields, indexFields);
        
        assertThat(canSatisfy).isFalse();
    }
    
    @Test
    void testESRValidationWithReverseTraversal() {
        // Test full ESR validation with reverse traversal
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("userId", SortDirection.ASC),     // equality
                new IndexField("status", SortDirection.ASC),     // equality
                new IndexField("createdAt", SortDirection.DESC), // sort (will need reverse)
                new IndexField("score", SortDirection.ASC)       // range
        );
        
        Set<String> equalityFields = new HashSet<>();
        equalityFields.add("userId");
        equalityFields.add("status");
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.ASC)  // opposite to index direction
        );
        
        Set<String> rangeFields = new HashSet<>();
        rangeFields.add("score");
        
        ReverseTraversalValidator.ESRValidationResult result = 
                reverseValidator.validateESRWithReverseTraversal(
                        equalityFields, sortFields, rangeFields, indexFields);
        
        assertThat(result.isValid()).isTrue();
        assertThat(result.needsReverseTraversal()).isTrue();
    }
    
    @Test
    void testESRValidationRangeAndSortSameFieldConflict() {
        // Range and sort on same field should be invalid for reverse traversal
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("userId", SortDirection.ASC),
                new IndexField("createdAt", SortDirection.DESC)
        );
        
        Set<String> equalityFields = new HashSet<>();
        equalityFields.add("userId");
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.ASC)
        );
        
        Set<String> rangeFields = new HashSet<>();
        rangeFields.add("createdAt"); // Same field as sort
        
        ReverseTraversalValidator.ESRValidationResult result = 
                reverseValidator.validateESRWithReverseTraversal(
                        equalityFields, sortFields, rangeFields, indexFields);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("reverse traversal");
    }
    
    @Test
    void testESRMatcherWithReverseTraversalDetails() {
        // Test the enhanced ESR matcher with detailed reverse traversal info
        
        MongoIndex index = new MongoIndex("userId_1_createdAt_-1", Arrays.asList(
                new IndexField("userId", SortDirection.ASC),
                new IndexField("createdAt", SortDirection.DESC)
        ));
        
        QueryAnalysis analysis = QueryAnalysis.simple(
                Set.of("userId"), 
                Set.of() // no range fields
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.ASC) // opposite to index direction
        );
        
        ESRMatcher.ESRMatchResult result = esrMatcher.indexMatchesWithDetails(
                analysis, sortFields, index);
        
        assertThat(result.matches()).isTrue();
        assertThat(result.needsReverseTraversal()).isTrue();
        assertThat(result.getReason()).isEqualTo("Perfect ESR match");
    }
    
    @Test
    void testESRMatcherNormalTraversal() {
        // Test when normal traversal is sufficient (no reverse needed)
        
        MongoIndex index = new MongoIndex("userId_1_createdAt_-1", Arrays.asList(
                new IndexField("userId", SortDirection.ASC),
                new IndexField("createdAt", SortDirection.DESC)
        ));
        
        QueryAnalysis analysis = QueryAnalysis.simple(
                Set.of("userId"), 
                Set.of()
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.DESC) // same as index direction
        );
        
        ESRMatcher.ESRMatchResult result = esrMatcher.indexMatchesWithDetails(
                analysis, sortFields, index);
        
        assertThat(result.matches()).isTrue();
        assertThat(result.needsReverseTraversal()).isFalse();
    }
    
    @Test
    void testComplexESRWithPartialReverseRequirement() {
        // Complex scenario with multiple sort fields where only some need reverse
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("category", SortDirection.ASC),
                new IndexField("status", SortDirection.DESC),
                new IndexField("createdAt", SortDirection.ASC),
                new IndexField("score", SortDirection.DESC)
        );
        
        Set<String> equalityFields = new HashSet<>();
        equalityFields.add("category");
        equalityFields.add("status");
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.DESC), // opposite to index
                new SortField("score", SortDirection.ASC)       // opposite to index
        );
        
        Set<String> rangeFields = new HashSet<>();
        
        ReverseTraversalValidator.ESRValidationResult result = 
                reverseValidator.validateESRWithReverseTraversal(
                        equalityFields, sortFields, rangeFields, indexFields);
        
        assertThat(result.isValid()).isTrue();
        assertThat(result.needsReverseTraversal()).isTrue();
    }
    
    @Test
    void testReverseTraversalBenefitAnalysis() {
        // Test the benefit analysis for reverse traversal
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("status", SortDirection.ASC),
                new IndexField("createdAt", SortDirection.DESC)
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.ASC) // opposite direction
        );
        
        Set<String> rangeFields = new HashSet<>();
        
        boolean beneficial = reverseValidator.isReverseTraversalBeneficial(
                sortFields, rangeFields, indexFields);
        
        assertThat(beneficial).isTrue();
    }
    
    @Test
    void testReverseTraversalNotBeneficialWithRangeConflict() {
        // Reverse traversal should not be beneficial when range and sort conflict
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("score", SortDirection.DESC)
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("score", SortDirection.ASC)
        );
        
        Set<String> rangeFields = new HashSet<>();
        rangeFields.add("score"); // Same field as sort
        
        boolean beneficial = reverseValidator.isReverseTraversalBeneficial(
                sortFields, rangeFields, indexFields);
        
        assertThat(beneficial).isFalse();
    }
    
    @Test
    void testEmptySortFieldsAlwaysValid() {
        // Empty sort fields should always be valid for reverse traversal
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("userId", SortDirection.ASC)
        );
        
        List<SortField> sortFields = Arrays.asList(); // empty
        Set<String> rangeFields = new HashSet<>();
        Set<String> equalityFields = new HashSet<>();
        equalityFields.add("userId");
        
        boolean canReverse = reverseValidator.canUseReverseTraversal(
                sortFields, rangeFields, equalityFields, indexFields);
        
        assertThat(canReverse).isTrue();
    }
    
    @Test
    void testSortFieldNotInIndex() {
        // Sort field not present in index should fail validation
        
        List<IndexField> indexFields = Arrays.asList(
                new IndexField("userId", SortDirection.ASC)
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("nonExistentField", SortDirection.ASC)
        );
        
        Set<String> rangeFields = new HashSet<>();
        Set<String> equalityFields = new HashSet<>();
        
        boolean canSatisfy = reverseValidator.canSatisfySortWithReverseTraversal(
                sortFields, indexFields);
        
        assertThat(canSatisfy).isFalse();
    }
}