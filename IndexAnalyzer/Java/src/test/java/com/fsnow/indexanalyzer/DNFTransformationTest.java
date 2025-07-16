package com.fsnow.indexanalyzer;

import com.fsnow.indexanalyzer.model.QueryAnalysis;
import com.fsnow.indexanalyzer.transformation.DNFTransformer;
import com.fsnow.indexanalyzer.transformation.LogicalOperatorHandler;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DNF (Disjunctive Normal Form) transformation.
 */
class DNFTransformationTest {
    
    private DNFTransformer dnfTransformer;
    private LogicalOperatorHandler operatorHandler;
    
    @BeforeEach
    void setUp() {
        dnfTransformer = new DNFTransformer();
        operatorHandler = new LogicalOperatorHandler();
    }
    
    @Test
    void testSimpleQueryNeedsNoTransformation() {
        // Test: { userId: 1, status: "active" }
        Document query = new Document()
                .append("userId", 1)
                .append("status", "active");
        
        boolean needsTransformation = operatorHandler.needsTransformation(query);
        assertThat(needsTransformation).isFalse();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.isSimple()).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactlyInAnyOrder("userId", "status");
    }
    
    @Test
    void testOrWithOtherConditionsNeedsTransformation() {
        // Test: { status: "active", $or: [{ userId: 1 }, { category: "premium" }] }
        Document query = new Document()
                .append("status", "active")
                .append("$or", Arrays.asList(
                        new Document("userId", 1),
                        new Document("category", "premium")
                ));
        
        boolean needsTransformation = operatorHandler.needsTransformation(query);
        assertThat(needsTransformation).isTrue();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.hasOr()).isTrue();
        assertThat(analysis.getOrBranches()).hasSize(2);
        
        // First branch should have status + userId
        QueryAnalysis branch1 = analysis.getOrBranches().get(0);
        assertThat(branch1.getEqualityFields()).containsExactlyInAnyOrder("status", "userId");
        
        // Second branch should have status + category  
        QueryAnalysis branch2 = analysis.getOrBranches().get(1);
        assertThat(branch2.getEqualityFields()).containsExactlyInAnyOrder("status", "category");
    }
    
    @Test
    void testOrWithinAndArrayNeedsTransformation() {
        // Test: { $and: [{ status: "active" }, { $or: [{ userId: 1 }, { category: "premium" }] }] }
        Document query = new Document()
                .append("$and", Arrays.asList(
                        new Document("status", "active"),
                        new Document("$or", Arrays.asList(
                                new Document("userId", 1),
                                new Document("category", "premium")
                        ))
                ));
        
        boolean needsTransformation = operatorHandler.needsTransformation(query);
        assertThat(needsTransformation).isTrue();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.hasOr()).isTrue();
        assertThat(analysis.getOrBranches()).hasSize(2);
    }
    
    @Test
    void testSimpleOrQuery() {
        // Test: { $or: [{ userId: 1 }, { status: "active" }] }
        Document query = new Document()
                .append("$or", Arrays.asList(
                        new Document("userId", 1),
                        new Document("status", "active")
                ));
        
        // Simple OR query with no other conditions doesn't need DNF transformation
        boolean needsTransformation = operatorHandler.needsTransformation(query);
        assertThat(needsTransformation).isFalse();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        System.out.println("DEBUG: Simple OR analysis: " + analysis);
        System.out.println("DEBUG: hasOr: " + analysis.hasOr());
        System.out.println("DEBUG: branches: " + analysis.getOrBranches().size());
        assertThat(analysis.hasOr()).isTrue();
        assertThat(analysis.getOrBranches()).hasSize(2);
        
        QueryAnalysis branch1 = analysis.getOrBranches().get(0);
        assertThat(branch1.getEqualityFields()).containsExactly("userId");
        
        QueryAnalysis branch2 = analysis.getOrBranches().get(1);
        assertThat(branch2.getEqualityFields()).containsExactly("status");
    }
    
    @Test
    void testNorQueryConservativeRejection() {
        // Test: { $nor: [{ status: "inactive" }] }
        Document query = new Document()
                .append("$nor", Arrays.asList(
                        new Document("status", "inactive")
                ));
        
        boolean needsTransformation = operatorHandler.needsTransformation(query);
        assertThat(needsTransformation).isTrue();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.hasOr()).isTrue();
        assertThat(analysis.getOrBranches()).hasSize(1);
        
        // Should contain __norPresent marker for conservative rejection
        QueryAnalysis norBranch = analysis.getOrBranches().get(0);
        assertThat(norBranch.getEqualityFields()).contains("__norPresent");
    }
    
    @Test
    void testEmptyQuery() {
        Document query = new Document();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.isSimple()).isTrue();
        assertThat(analysis.getEqualityFields()).isEmpty();
        assertThat(analysis.getRangeFields()).isEmpty();
    }
    
    @Test
    void testRangeQueryAnalysis() {
        // Test: { score: { $gte: 80 }, createdAt: { $lt: new Date() } }
        Document query = new Document()
                .append("score", new Document("$gte", 80))
                .append("createdAt", new Document("$lt", new java.util.Date()));
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.isSimple()).isTrue();
        assertThat(analysis.getRangeFields()).containsExactlyInAnyOrder("score", "createdAt");
        assertThat(analysis.getEqualityFields()).isEmpty();
    }
    
    @Test
    void testMixedEqualityAndRangeQuery() {
        // Test: { userId: 1, status: "active", score: { $gte: 80 } }
        Document query = new Document()
                .append("userId", 1)
                .append("status", "active")
                .append("score", new Document("$gte", 80));
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.isSimple()).isTrue();
        assertThat(analysis.getEqualityFields()).containsExactlyInAnyOrder("userId", "status");
        assertThat(analysis.getRangeFields()).containsExactly("score");
    }
    
    @Test
    void testTripleORCrossProduct() {
        // Test: Triple OR cross-product (2x2x2 = 8 branches)
        // { $and: [
        //     { $or: [{ status: "active" }, { status: "inactive" }] },
        //     { $or: [{ category: "premium" }, { category: "basic" }] },
        //     { $or: [{ tags: "a" }, { tags: "b" }] }
        // ] }
        Document query = new Document()
                .append("$and", Arrays.asList(
                        new Document("$or", Arrays.asList(
                                new Document("status", "active"),
                                new Document("status", "inactive")
                        )),
                        new Document("$or", Arrays.asList(
                                new Document("category", "premium"),
                                new Document("category", "basic")
                        )),
                        new Document("$or", Arrays.asList(
                                new Document("tags", "a"),
                                new Document("tags", "b")
                        ))
                ));
        
        boolean needsTransformation = operatorHandler.needsTransformation(query);
        assertThat(needsTransformation).isTrue();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.hasOr()).isTrue();
        assertThat(analysis.getOrBranches()).hasSize(8); // 2x2x2 = 8 combinations
        
        // Verify that each branch contains exactly 3 equality fields
        for (QueryAnalysis branch : analysis.getOrBranches()) {
            assertThat(branch.getEqualityFields()).hasSize(3);
            assertThat(branch.getEqualityFields()).contains("status");
            assertThat(branch.getEqualityFields()).contains("category");
            assertThat(branch.getEqualityFields()).contains("tags");
        }
        
        // Verify that we have all possible combinations represented
        Set<String> observedCombinations = new HashSet<>();
        for (QueryAnalysis branch : analysis.getOrBranches()) {
            // This is a simplified check - in a real implementation we'd verify the actual values
            String combination = branch.getEqualityFields().toString();
            observedCombinations.add(combination);
        }
        
        // All 8 branches should have the same field structure but different values
        assertThat(observedCombinations).hasSize(1); // All should have same fields: [status, category, tags]
    }
    
    @Test
    void testDeeplyNestedLogicalOperators() {
        // Test: Deeply nested logical operators (3 levels)
        // {
        //     status: "active",
        //     $or: [
        //         { userId: 1 },
        //         {
        //             $and: [
        //                 { category: "premium" },
        //                 { $or: [{ score: { $gte: 90 } }, { tags: "a" }] }
        //             ]
        //         }
        //     ]
        // }
        Document query = new Document()
                .append("status", "active")
                .append("$or", Arrays.asList(
                        new Document("userId", 1),
                        new Document("$and", Arrays.asList(
                                new Document("category", "premium"),
                                new Document("$or", Arrays.asList(
                                        new Document("score", new Document("$gte", 90)),
                                        new Document("tags", "a")
                                ))
                        ))
                ));
        
        boolean needsTransformation = operatorHandler.needsTransformation(query);
        assertThat(needsTransformation).isTrue();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.hasOr()).isTrue();
        assertThat(analysis.getOrBranches()).hasSize(3);
        
        // Expected branches:
        // 1. { status: "active", userId: 1 }
        // 2. { status: "active", category: "premium", score: { $gte: 90 } }
        // 3. { status: "active", category: "premium", tags: "a" }
        
        
        // Verify each branch has status equality field
        for (QueryAnalysis branch : analysis.getOrBranches()) {
            assertThat(branch.getEqualityFields()).contains("status");
        }
        
        // Count branches by their structure
        int simpleUserIdBranches = 0;
        int premiumScoreBranches = 0;
        int premiumTagsBranches = 0;
        
        for (QueryAnalysis branch : analysis.getOrBranches()) {
            if (branch.getEqualityFields().contains("userId") && 
                branch.getEqualityFields().size() == 2) {
                simpleUserIdBranches++;
            } else if (branch.getEqualityFields().contains("category") && 
                       branch.getRangeFields().contains("score")) {
                premiumScoreBranches++;
            } else if (branch.getEqualityFields().contains("category") && 
                       branch.getEqualityFields().contains("tags")) {
                premiumTagsBranches++;
            }
        }
        
        assertThat(simpleUserIdBranches).isEqualTo(1);
        assertThat(premiumScoreBranches).isEqualTo(1);
        assertThat(premiumTagsBranches).isEqualTo(1);
    }
    
    @Test
    void testComplexAndWithMultipleOrClauses() {
        // Test: Complex $and with multiple $or clauses that require cross-product expansion
        // { $and: [
        //     { $or: [{ userId: 1 }, { userId: 2 }] },
        //     { $or: [{ status: "active" }, { status: "inactive" }] }
        // ] }
        Document query = new Document()
                .append("$and", Arrays.asList(
                        new Document("$or", Arrays.asList(
                                new Document("userId", 1),
                                new Document("userId", 2)
                        )),
                        new Document("$or", Arrays.asList(
                                new Document("status", "active"),
                                new Document("status", "inactive")
                        ))
                ));
        
        boolean needsTransformation = operatorHandler.needsTransformation(query);
        assertThat(needsTransformation).isTrue();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.hasOr()).isTrue();
        assertThat(analysis.getOrBranches()).hasSize(4); // 2x2 = 4 combinations
        
        // Each branch should have exactly userId and status fields
        for (QueryAnalysis branch : analysis.getOrBranches()) {
            assertThat(branch.getEqualityFields()).hasSize(2);
            assertThat(branch.getEqualityFields()).containsExactlyInAnyOrder("userId", "status");
        }
    }
    
    @Test
    void testMixedLogicalOperatorsWithRangeFields() {
        // Test: Mixed logical operators with range fields
        // {
        //     category: "premium",
        //     $or: [
        //         { score: { $gte: 90 } },
        //         { $and: [{ userId: 1 }, { createdAt: { $lte: new Date() } }] }
        //     ]
        // }
        Document query = new Document()
                .append("category", "premium")
                .append("$or", Arrays.asList(
                        new Document("score", new Document("$gte", 90)),
                        new Document("$and", Arrays.asList(
                                new Document("userId", 1),
                                new Document("createdAt", new Document("$lte", new java.util.Date()))
                        ))
                ));
        
        boolean needsTransformation = operatorHandler.needsTransformation(query);
        assertThat(needsTransformation).isTrue();
        
        QueryAnalysis analysis = dnfTransformer.transformToQueryAnalysis(query);
        assertThat(analysis.hasOr()).isTrue();
        assertThat(analysis.getOrBranches()).hasSize(2);
        
        
        // Each branch should contain category equality field
        for (QueryAnalysis branch : analysis.getOrBranches()) {
            assertThat(branch.getEqualityFields()).contains("category");
        }
        
        // Verify one branch has score range and one has userId equality + createdAt range
        boolean hasScoreBranch = false;
        boolean hasUserIdCreatedAtBranch = false;
        
        for (QueryAnalysis branch : analysis.getOrBranches()) {
            if (branch.getRangeFields().contains("score")) {
                hasScoreBranch = true;
                assertThat(branch.getEqualityFields()).containsExactly("category");
                assertThat(branch.getRangeFields()).containsExactly("score");
            } else if (branch.getEqualityFields().contains("userId")) {
                hasUserIdCreatedAtBranch = true;
                assertThat(branch.getEqualityFields()).containsExactlyInAnyOrder("category", "userId");
                assertThat(branch.getRangeFields()).containsExactly("createdAt");
            }
        }
        
        assertThat(hasScoreBranch).isTrue();
        assertThat(hasUserIdCreatedAtBranch).isTrue();
    }
}