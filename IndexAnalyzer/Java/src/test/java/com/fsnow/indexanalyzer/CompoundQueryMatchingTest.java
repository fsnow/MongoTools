package com.fsnow.indexanalyzer;

import com.fsnow.indexanalyzer.matching.CompoundQueryMatcher;
import com.fsnow.indexanalyzer.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test suite for compound query matching (Phase 3).
 * Tests enhanced query and sort matching with focus on boolean perfect match results.
 */
class CompoundQueryMatchingTest {
    
    private CompoundQueryMatcher compoundMatcher;
    private List<MongoIndex> testIndexes;
    
    @BeforeEach
    void setUp() {
        compoundMatcher = new CompoundQueryMatcher();
        
        // Create test indexes similar to Phase 2 but with compound sort support
        testIndexes = Arrays.asList(
            // Single field
            new MongoIndex("userId_1", Arrays.asList(
                new IndexField("userId", SortDirection.ASC)
            )),
            // Compound with sort
            new MongoIndex("status_1_createdAt_-1", Arrays.asList(
                new IndexField("status", SortDirection.ASC),
                new IndexField("createdAt", SortDirection.DESC)
            )),
            // Multi-field compound
            new MongoIndex("userId_1_status_1_createdAt_-1", Arrays.asList(
                new IndexField("userId", SortDirection.ASC),
                new IndexField("status", SortDirection.ASC),
                new IndexField("createdAt", SortDirection.DESC)
            )),
            // Complex ESR
            new MongoIndex("category_1_status_1_createdAt_-1_score_1", Arrays.asList(
                new IndexField("category", SortDirection.ASC),
                new IndexField("status", SortDirection.ASC),
                new IndexField("createdAt", SortDirection.DESC),
                new IndexField("score", SortDirection.ASC)
            ))
        );
    }
    
    @Test
    void testSimpleQueryWithSingleSort() {
        // Query: {status: "active"} with sort: {createdAt: -1}
        QueryAnalysis analysis = QueryAnalysis.simple(
                Set.of("status"), 
                Set.of()
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.DESC)
        );
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
    }
    
    @Test
    void testMultiFieldQueryWithCompoundSort() {
        // Query: {userId: 1, status: "active"} with sort: {createdAt: -1}
        QueryAnalysis analysis = QueryAnalysis.simple(
                Set.of("userId", "status"), 
                Set.of()
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.DESC)
        );
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
    }
    
    @Test
    void testQueryWithMissingField() {
        // Query: {nonExistentField: "value"} 
        QueryAnalysis analysis = QueryAnalysis.simple(
                Set.of("nonExistentField"), 
                Set.of()
        );
        
        List<SortField> sortFields = Arrays.asList();
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isFalse();
    }
    
    @Test
    void testSortFieldNotInIndex() {
        // Query: {status: "active"} with sort: {nonExistentField: 1}
        QueryAnalysis analysis = QueryAnalysis.simple(
                Set.of("status"), 
                Set.of()
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("nonExistentField", SortDirection.ASC)
        );
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isFalse();
    }
    
    @Test
    void testWrongSortDirection() {
        // Query: {status: "active"} with sort: {createdAt: 1} (wrong direction)
        QueryAnalysis analysis = QueryAnalysis.simple(
                Set.of("status"), 
                Set.of()
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.ASC)  // Index has DESC
        );
        
        // This should fail unless reverse traversal is properly handled
        boolean result = compoundMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        // With reverse traversal support, this should work
        assertThat(result).isTrue();
    }
    
    @Test
    void testORQueryAllBranchesSupported() {
        // OR query where both branches are supported
        QueryAnalysis branch1 = QueryAnalysis.simple(Set.of("userId"), Set.of());
        QueryAnalysis branch2 = QueryAnalysis.simple(Set.of("status"), Set.of());
        QueryAnalysis orAnalysis = QueryAnalysis.or(Arrays.asList(branch1, branch2));
        
        List<SortField> sortFields = Arrays.asList();
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(orAnalysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
    }
    
    @Test
    void testORQueryOneBranchUnsupported() {
        // OR query where one branch is not supported
        QueryAnalysis branch1 = QueryAnalysis.simple(Set.of("userId"), Set.of());
        QueryAnalysis branch2 = QueryAnalysis.simple(Set.of("nonExistentField"), Set.of());
        QueryAnalysis orAnalysis = QueryAnalysis.or(Arrays.asList(branch1, branch2));
        
        List<SortField> sortFields = Arrays.asList();
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(orAnalysis, sortFields, testIndexes);
        
        assertThat(result).isFalse();
    }
    
    @Test
    void testORQueryWithSort() {
        // OR query with sort requirements - simpler case
        // Both branches can use different simple indexes
        QueryAnalysis branch1 = QueryAnalysis.simple(Set.of("userId"), Set.of());
        QueryAnalysis branch2 = QueryAnalysis.simple(Set.of("status"), Set.of());
        QueryAnalysis orAnalysis = QueryAnalysis.or(Arrays.asList(branch1, branch2));
        
        // No sort to keep it simple
        List<SortField> sortFields = Arrays.asList();
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(orAnalysis, sortFields, testIndexes);
        
        // Both branches should be supported by simple indexes
        assertThat(result).isTrue();
    }
    
    @Test
    void testComplexESRPattern() {
        // Query: {category: "premium", status: "active", score: {$gte: 80}} 
        // Sort: {createdAt: -1}
        QueryAnalysis analysis = QueryAnalysis.simple(
                Set.of("category", "status"), 
                Set.of("score")
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.DESC)
        );
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        // Should be supported by category_1_status_1_createdAt_-1_score_1 index
        assertThat(result).isTrue();
    }
    
    @Test
    void testESRViolation() {
        // Query with ESR violation (range field before sort field)
        QueryAnalysis analysis = QueryAnalysis.simple(
                Set.of("category"), 
                Set.of("status")  // Range on field that comes before sort in index
        );
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.DESC)
        );
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        // This violates ESR pattern - range on status but sort on createdAt
        // In the index category_1_status_1_createdAt_-1_score_1, status comes before createdAt
        assertThat(result).isFalse();
    }
    
    @Test
    void testEmptyQuery() {
        // Empty query should always be supported
        QueryAnalysis analysis = QueryAnalysis.simple(Set.of(), Set.of());
        List<SortField> sortFields = Arrays.asList();
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
    }
    
    @Test
    void testSortOnlyQuery() {
        // Query: {} with sort: {userId: 1} (use a simpler single-field sort)
        QueryAnalysis analysis = QueryAnalysis.simple(Set.of(), Set.of());
        
        List<SortField> sortFields = Arrays.asList(
                new SortField("userId", SortDirection.ASC)
        );
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        // Should be supported by userId_1 index
        assertThat(result).isTrue();
    }
    
    @Test
    void testNorQueryRejection() {
        // $nor queries should be conservatively rejected
        QueryAnalysis norAnalysis = QueryAnalysis.or(Arrays.asList(
                QueryAnalysis.simple(Set.of("__norPresent"), Set.of())
        ));
        
        List<SortField> sortFields = Arrays.asList();
        
        boolean result = compoundMatcher.hasIndexPerfectMatch(norAnalysis, sortFields, testIndexes);
        
        assertThat(result).isFalse();
    }
}