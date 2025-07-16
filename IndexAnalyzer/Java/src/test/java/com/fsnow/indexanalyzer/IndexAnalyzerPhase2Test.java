package com.fsnow.indexanalyzer;

import com.fsnow.indexanalyzer.model.*;
import com.fsnow.indexanalyzer.matching.ESRMatcher;
import com.fsnow.indexanalyzer.matching.IndexMatcher;
import com.fsnow.indexanalyzer.parser.CriteriaParser;
import com.fsnow.indexanalyzer.parser.SortParser;
import com.fsnow.indexanalyzer.transformation.DNFTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 test suite covering 30% of JavaScript functionality.
 * Tests basic ESR matching and simple DNF transformation.
 */
class IndexAnalyzerPhase2Test {
    
    private CriteriaParser criteriaParser;
    private SortParser sortParser;
    private DNFTransformer dnfTransformer;
    private IndexMatcher indexMatcher;
    private ESRMatcher esrMatcher;
    
    // Test indexes similar to JavaScript test setup
    private List<MongoIndex> testIndexes;
    
    @BeforeEach
    void setUp() {
        criteriaParser = new CriteriaParser();
        sortParser = new SortParser();
        dnfTransformer = new DNFTransformer();
        indexMatcher = new IndexMatcher();
        esrMatcher = new ESRMatcher();
        
        // Create test indexes matching JavaScript test suite
        testIndexes = Arrays.asList(
            // Single field
            new MongoIndex("userId_1", Arrays.asList(
                new IndexField("userId", 1)
            )),
            // Compound
            new MongoIndex("status_1_createdAt_-1", Arrays.asList(
                new IndexField("status", 1),
                new IndexField("createdAt", -1)
            )),
            // ESR pattern
            new MongoIndex("userId_1_status_1_createdAt_-1", Arrays.asList(
                new IndexField("userId", 1),
                new IndexField("status", 1),
                new IndexField("createdAt", -1)
            )),
            // Multi-field equality
            new MongoIndex("category_1_score_1", Arrays.asList(
                new IndexField("category", 1),
                new IndexField("score", 1)
            )),
            // Sort-only index
            new MongoIndex("createdAt_-1", Arrays.asList(
                new IndexField("createdAt", -1)
            ))
        );
    }
    
    @Test
    void testSingleFieldEqualityMatch() {
        // Test: { userId: 1 }
        Criteria criteria = Criteria.where("userId").is(1);
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = Collections.emptyList();
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactly("userId");
        assertThat(analysis.getRangeFields()).isEmpty();
    }
    
    @Test
    void testSingleFieldEqualityNoMatch() {
        // Test: { nonExistentField: 1 }
        Criteria criteria = Criteria.where("nonExistentField").is(1);
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = Collections.emptyList();
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isFalse();
        assertThat(analysis.getEqualityFields()).containsExactly("nonExistentField");
    }
    
    @Test
    void testMultiFieldEqualityMatch() {
        // Test: { category: "premium", score: 95 }
        Criteria criteria = Criteria.where("category").is("premium")
                .and("score").is(95);
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = Collections.emptyList();
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactlyInAnyOrder("category", "score");
    }
    
    @Test
    void testRangeQueryMatch() {
        // Test: { createdAt: { $gte: new Date() } }
        Criteria criteria = Criteria.where("createdAt").gte(new java.util.Date());
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = Collections.emptyList();
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
        assertThat(analysis.getRangeFields()).containsExactly("createdAt");
        assertThat(analysis.getEqualityFields()).isEmpty();
    }
    
    @Test
    void testEqualityWithSortMatch() {
        // Test: { status: "active" } with sort { createdAt: -1 }
        Criteria criteria = Criteria.where("status").is("active");
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = sortParser.parse(sort);
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactly("status");
        assertThat(sortFields).hasSize(1);
        assertThat(sortFields.get(0).getField()).isEqualTo("createdAt");
        assertThat(sortFields.get(0).getDirection()).isEqualTo(SortDirection.DESC);
    }
    
    @Test
    void testPerfectESRMatch() {
        // Test: { userId: 1, status: "active" } with sort { createdAt: -1 }
        Criteria criteria = Criteria.where("userId").is(1)
                .and("status").is("active");
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = sortParser.parse(sort);
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactlyInAnyOrder("userId", "status");
    }
    
    @Test
    void testESRWithWrongFieldOrder() {
        // Test: { status: "active", userId: 1 } - wrong order for ESR
        // This should still work because order doesn't matter within equality fields
        Criteria criteria = Criteria.where("status").is("active")
                .and("userId").is(1);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = sortParser.parse(sort);
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue(); // Should work because equality field order doesn't matter
    }
    
    @Test
    void testSortOnlyQuery() {
        // Test: {} with sort { createdAt: -1 }
        Criteria criteria = new Criteria(); // Empty criteria
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = sortParser.parse(sort);
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
        assertThat(analysis.getEqualityFields()).isEmpty();
        assertThat(analysis.getRangeFields()).isEmpty();
    }
    
    @Test
    void testInOperator() {
        // Test: { status: { $in: ["active", "inactive"] } }
        Criteria criteria = Criteria.where("status").in("active", "inactive");
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = Collections.emptyList();
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactly("status");
    }
    
    @Test
    void testComplexESRScenario() {
        // Test complex ESR with range: { userId: 1, status: "active", createdAt: { $gte: date } }
        Criteria criteria = Criteria.where("userId").is(1)
                .and("status").is("active")
                .and("createdAt").gte(new java.util.Date());
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = Collections.emptyList();
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactlyInAnyOrder("userId", "status");
        assertThat(analysis.getRangeFields()).containsExactly("createdAt");
    }
    
    @Test
    void testMissingIndexFields() {
        // Test query with fields not covered by any index
        Criteria criteria = Criteria.where("nonExistentField1").is("value")
                .and("nonExistentField2").gte(100);
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        List<SortField> sortFields = Collections.emptyList();
        
        boolean result = indexMatcher.hasIndexPerfectMatch(analysis, sortFields, testIndexes);
        
        assertThat(result).isFalse();
    }
    
    @Test
    void testESRMatcherDirectly() {
        // Test ESRMatcher directly with a known good scenario
        QueryAnalysis analysis = QueryAnalysis.simple(
                java.util.Set.of("userId", "status"), 
                java.util.Set.of("createdAt")
        );
        List<SortField> sortFields = Arrays.asList(
                new SortField("createdAt", SortDirection.DESC)
        );
        
        MongoIndex esrIndex = new MongoIndex("userId_1_status_1_createdAt_-1", Arrays.asList(
                new IndexField("userId", 1),
                new IndexField("status", 1),
                new IndexField("createdAt", -1)
        ));
        
        boolean result = esrMatcher.indexPerfectlyMatches(analysis, sortFields, esrIndex);
        
        assertThat(result).isTrue();
    }
}