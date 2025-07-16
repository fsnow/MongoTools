package com.fsnow.indexanalyzer;

import com.fsnow.indexanalyzer.exception.InvalidNamespaceException;
import com.fsnow.indexanalyzer.model.QueryAnalysis;
import com.fsnow.indexanalyzer.model.SortDirection;
import com.fsnow.indexanalyzer.model.SortField;
import com.fsnow.indexanalyzer.parser.CriteriaParser;
import com.fsnow.indexanalyzer.parser.SortParser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Basic tests for Phase 1 components.
 */
class IndexAnalyzerBasicTest {
    
    private final CriteriaParser criteriaParser = new CriteriaParser();
    private final SortParser sortParser = new SortParser();
    
    @Test
    void testSimpleEqualityCriteria() {
        // Test: { userId: 1 }
        Criteria criteria = Criteria.where("userId").is(1);
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        
        assertThat(analysis.isSimple()).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactly("userId");
        assertThat(analysis.getRangeFields()).isEmpty();
    }
    
    @Test
    void testRangeCriteria() {
        // Test: { score: { $gte: 80 } }
        Criteria criteria = Criteria.where("score").gte(80);
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        
        assertThat(analysis.isSimple()).isTrue();
        assertThat(analysis.getEqualityFields()).isEmpty();
        assertThat(analysis.getRangeFields()).containsExactly("score");
    }
    
    @Test
    void testMultipleFieldsCriteria() {
        // Test: { userId: 1, status: "active", score: { $gte: 80 } }
        Criteria criteria = Criteria.where("userId").is(1)
                .and("status").is("active")
                .and("score").gte(80);
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        
        assertThat(analysis.isSimple()).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactlyInAnyOrder("userId", "status");
        assertThat(analysis.getRangeFields()).containsExactly("score");
    }
    
    @Test
    void testInOperator() {
        // Test: { status: { $in: ["active", "pending"] } }
        Criteria criteria = Criteria.where("status").in("active", "pending");
        
        QueryAnalysis analysis = criteriaParser.parse(criteria);
        
        assertThat(analysis.isSimple()).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactly("status");
        assertThat(analysis.getRangeFields()).isEmpty();
    }
    
    @Test
    void testSortParser() {
        // Test sort: { createdAt: -1, score: 1 }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                       .and(Sort.by(Sort.Direction.ASC, "score"));
        
        List<SortField> sortFields = sortParser.parse(sort);
        
        assertThat(sortFields).hasSize(2);
        assertThat(sortFields.get(0).getField()).isEqualTo("createdAt");
        assertThat(sortFields.get(0).getDirection()).isEqualTo(SortDirection.DESC);
        assertThat(sortFields.get(1).getField()).isEqualTo("score");
        assertThat(sortFields.get(1).getDirection()).isEqualTo(SortDirection.ASC);
    }
    
    @Test
    void testEmptyCriteria() {
        QueryAnalysis analysis = criteriaParser.parse(null);
        
        assertThat(analysis.isSimple()).isTrue();
        assertThat(analysis.getEqualityFields()).isEmpty();
        assertThat(analysis.getRangeFields()).isEmpty();
    }
    
    @Test
    void testEmptySort() {
        List<SortField> sortFields = sortParser.parse(null);
        assertThat(sortFields).isEmpty();
        
        sortFields = sortParser.parse(Sort.unsorted());
        assertThat(sortFields).isEmpty();
    }
    
    @Test
    void testInvalidNamespaceShouldThrowException() {
        // Test invalid namespace formats that should throw InvalidNamespaceException
        String connectionString = "mongodb://localhost:27017"; // Use a dummy connection string for testing
        
        try (IndexAnalyzer analyzer = new IndexAnalyzer(connectionString)) {
            Criteria criteria = Criteria.where("userId").is(1);
            Sort sort = Sort.by("createdAt");
            
            // Test various invalid namespace formats
            assertThrows(InvalidNamespaceException.class, () -> 
                analyzer.analyzeIndexCoverage(criteria, sort, "invalid_namespace"));
            
            assertThrows(InvalidNamespaceException.class, () -> 
                analyzer.analyzeIndexCoverage(criteria, sort, ""));
            
            assertThrows(InvalidNamespaceException.class, () -> 
                analyzer.analyzeIndexCoverage(criteria, sort, null));
            
            assertThrows(InvalidNamespaceException.class, () -> 
                analyzer.analyzeIndexCoverage(criteria, sort, "database."));
            
            assertThrows(InvalidNamespaceException.class, () -> 
                analyzer.analyzeIndexCoverage(criteria, sort, ".collection"));
            
            assertThrows(InvalidNamespaceException.class, () -> 
                analyzer.analyzeIndexCoverage(criteria, sort, "database.collection.extra"));
            
            assertThrows(InvalidNamespaceException.class, () -> 
                analyzer.analyzeIndexCoverage(criteria, sort, "no_dot_at_all"));
            
            assertThrows(InvalidNamespaceException.class, () -> 
                analyzer.analyzeIndexCoverage(criteria, sort, "multiple.dots.here"));
        }
    }
}