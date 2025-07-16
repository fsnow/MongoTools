package com.fsnow.indexanalyzer.transformation;

import com.fsnow.indexanalyzer.model.QueryAnalysis;
import com.fsnow.indexanalyzer.parser.CriteriaParser;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transforms MongoDB queries to Disjunctive Normal Form (DNF).
 * 
 * Examples of transformations:
 * - {a:1, $or:[{b:2}, {c:3}]} => {$or:[{a:1, b:2}, {a:1, c:3}]}
 * - {$and:[{a:1}, {$or:[{b:2}, {c:3}]}]} => {$or:[{a:1, b:2}, {a:1, c:3}]}
 * - Multiple OR: handled via cross product
 */
public class DNFTransformer {
    
    private static final Logger logger = LoggerFactory.getLogger(DNFTransformer.class);
    
    private final LogicalOperatorHandler operatorHandler;
    private final CriteriaParser criteriaParser;
    
    public DNFTransformer() {
        this.operatorHandler = new LogicalOperatorHandler();
        this.criteriaParser = new CriteriaParser();
    }
    
    /**
     * Transforms a query document to DNF and returns the analysis.
     */
    public QueryAnalysis transformToQueryAnalysis(Document query) {
        if (query == null || query.isEmpty()) {
            return QueryAnalysis.simple(Collections.emptySet(), Collections.emptySet());
        }
        
        // Handle $nor transformation (conservative approach)
        if (query.containsKey("$nor")) {
            logger.debug("Query contains $nor, marking for conservative rejection");
            return QueryAnalysis.or(Collections.singletonList(
                    QueryAnalysis.simple(Collections.singleton("__norPresent"), Collections.emptySet())
            ));
        }
        
        if (!operatorHandler.needsTransformation(query)) {
            // No transformation needed, analyze directly
            return analyzeSimpleDocument(query);
        }
        
        logger.debug("Applying DNF transformation to query: {}", query);
        
        List<DNFBranch> dnfBranches = transformToDNF(query);
        
        if (dnfBranches.size() == 1) {
            // Single branch, return as simple query
            return analyzeSimpleDocument(dnfBranches.get(0).getConditions());
        }
        
        // Multiple branches, create OR query analysis
        List<QueryAnalysis> branchAnalyses = new ArrayList<>();
        for (DNFBranch branch : dnfBranches) {
            QueryAnalysis branchAnalysis = analyzeSimpleDocument(branch.getConditions());
            branchAnalyses.add(branchAnalysis);
        }
        
        return QueryAnalysis.or(branchAnalyses);
    }
    
    /**
     * Transforms a query to DNF branches.
     */
    private List<DNFBranch> transformToDNF(Document query) {
        return transformToDNFWithContext(query, new Document());
    }
    
    /**
     * Transforms a query to DNF branches with context from parent levels.
     */
    private List<DNFBranch> transformToDNFWithContext(Document query, Document parentContext) {
        if (!operatorHandler.needsTransformation(query)) {
            // Merge parent context with current query
            Document merged = new Document();
            merged.putAll(parentContext);
            merged.putAll(query);
            return List.of(new DNFBranch(merged));
        }
        
        List<LogicalOperatorHandler.Conjunction> conjunctions = operatorHandler.extractConjunctions(query);
        List<DNFBranch> dnfBranches = operatorHandler.computeCrossProduct(conjunctions);
        
        // Apply recursive transformation if needed
        List<DNFBranch> finalBranches = new ArrayList<>();
        for (DNFBranch branch : dnfBranches) {
            Document branchConditions = branch.getConditions();
            
            if (operatorHandler.needsTransformation(branchConditions)) {
                // Recursively transform this branch with merged context
                Document newContext = new Document();
                newContext.putAll(parentContext);
                // Extract literal conditions from current level to add to context
                for (LogicalOperatorHandler.Conjunction conj : conjunctions) {
                    if (conj.getType() == LogicalOperatorHandler.Conjunction.Type.LITERAL) {
                        newContext.putAll(conj.getLiteralValue());
                    }
                }
                
                List<DNFBranch> subBranches = transformToDNFWithContext(branchConditions, newContext);
                finalBranches.addAll(subBranches);
            } else {
                // Merge parent context with branch conditions
                Document merged = new Document();
                merged.putAll(parentContext);
                merged.putAll(branchConditions);
                finalBranches.add(new DNFBranch(merged));
            }
        }
        
        return finalBranches;
    }
    
    /**
     * Analyzes a simple document (no logical operators) using CriteriaParser.
     */
    private QueryAnalysis analyzeSimpleDocument(Document document) {
        try {
            // Check for logical operators first - should not be analyzed as simple document
            if (document.containsKey("$or") || document.containsKey("$and") || document.containsKey("$nor")) {
                // This is actually a logical query, not a simple document
                // We need to parse it differently
                return parseLogicalDocument(document);
            }
            
            // For now, we'll create a simple analysis directly from the document
            return createSimpleAnalysisFromDocument(document);
            
        } catch (Exception e) {
            logger.warn("Failed to analyze document: {}", document, e);
            return QueryAnalysis.simple(Collections.emptySet(), Collections.emptySet());
        }
    }
    
    /**
     * Parses a document that contains logical operators.
     */
    private QueryAnalysis parseLogicalDocument(Document document) {
        if (document.containsKey("$or")) {
            @SuppressWarnings("unchecked")
            List<Document> orClauses = (List<Document>) document.get("$or");
            List<QueryAnalysis> branches = new ArrayList<>();
            
            // Check if there are other conditions at the same level
            Document remainingConditions = new Document(document);
            remainingConditions.remove("$or");
            
            for (Document clause : orClauses) {
                // Merge remaining conditions with each OR clause
                Document mergedClause = new Document();
                mergedClause.putAll(remainingConditions);
                mergedClause.putAll(clause);
                
                branches.add(createSimpleAnalysisFromDocument(mergedClause));
            }
            
            return QueryAnalysis.or(branches);
        }
        
        // For now, handle other logical operators as simple analysis
        return createSimpleAnalysisFromDocument(document);
    }
    
    /**
     * Creates a QueryAnalysis directly from a document.
     * This is a simplified version that will be enhanced later.
     */
    private QueryAnalysis createSimpleAnalysisFromDocument(Document document) {
        // First, flatten any remaining $and structures
        Document flattened = flattenDocument(document);
        
        QueryAnalysis.Builder builder = new QueryAnalysis.Builder();
        
        for (String fieldName : flattened.keySet()) {
            Object value = flattened.get(fieldName);
            
            // Skip logical operators - they should not be treated as fields
            if (fieldName.equals("$and") || fieldName.equals("$or") || fieldName.equals("$nor")) {
                continue;
            }
            
            if (value instanceof Document) {
                Document valueDoc = (Document) value;
                boolean hasRangeOp = valueDoc.keySet().stream()
                        .anyMatch(op -> op.equals("$gt") || op.equals("$gte") || 
                                      op.equals("$lt") || op.equals("$lte"));
                
                if (hasRangeOp) {
                    builder.addRangeField(fieldName);
                } else {
                    builder.addEqualityField(fieldName);
                }
            } else {
                // Direct value is equality
                builder.addEqualityField(fieldName);
            }
        }
        
        return builder.build();
    }
    
    /**
     * Flattens a document by expanding any $and structures.
     */
    private Document flattenDocument(Document document) {
        Document result = new Document();
        
        for (String fieldName : document.keySet()) {
            Object value = document.get(fieldName);
            
            if (fieldName.equals("$and") && value instanceof List) {
                // Flatten $and array into the result document
                @SuppressWarnings("unchecked")
                List<Document> andClauses = (List<Document>) value;
                for (Document clause : andClauses) {
                    Document flattenedClause = flattenDocument(clause);
                    result.putAll(flattenedClause);
                }
            } else {
                result.put(fieldName, value);
            }
        }
        
        return result;
    }
}