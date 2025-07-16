package com.fsnow.indexanalyzer.parser;

import com.fsnow.indexanalyzer.exception.CriteriaParsingException;
import com.fsnow.indexanalyzer.model.QueryAnalysis;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Parses Spring Data MongoDB Criteria objects to extract field conditions.
 */
public class CriteriaParser {
    
    private static final Logger logger = LoggerFactory.getLogger(CriteriaParser.class);
    
    /**
     * Parses a Criteria object and returns a QueryAnalysis.
     */
    public QueryAnalysis parse(Criteria criteria) {
        if (criteria == null) {
            // Empty criteria
            return QueryAnalysis.simple(Collections.emptySet(), Collections.emptySet());
        }
        
        try {
            Document criteriaDocument = extractCriteriaDocument(criteria);
            return analyzeCriteriaDocument(criteriaDocument);
        } catch (Exception e) {
            throw new CriteriaParsingException("Failed to parse criteria", e);
        }
    }
    
    /**
     * Extracts the internal Document from a Criteria object.
     */
    private Document extractCriteriaDocument(Criteria criteria) throws Exception {
        // Spring Data MongoDB provides getCriteriaObject() method
        Document document = criteria.getCriteriaObject();
        
        if (document != null && !document.isEmpty()) {
            return document;
        }
        
        // For chained criteria, we might need to look at the criteria chain
        Field criteriaChainField = Criteria.class.getDeclaredField("criteriaChain");
        criteriaChainField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Criteria> criteriaChain = (List<Criteria>) criteriaChainField.get(criteria);
        
        if (criteriaChain != null && !criteriaChain.isEmpty()) {
            // Merge all criteria in the chain
            Document merged = new Document();
            for (Criteria c : criteriaChain) {
                Document doc = c.getCriteriaObject();
                if (doc != null) {
                    merged.putAll(doc);
                }
            }
            return merged;
        }
        
        // Return empty document if no criteria found
        return new Document();
    }
    
    /**
     * Analyzes a criteria document to extract field types.
     */
    private QueryAnalysis analyzeCriteriaDocument(Document document) {
        // Check for top-level logical operators
        if (document.containsKey("$or")) {
            return handleOrOperator(document);
        }
        
        if (document.containsKey("$and")) {
            return handleAndOperator(document);
        }
        
        if (document.containsKey("$nor")) {
            // Conservative approach: mark as having OR (will be rejected)
            return QueryAnalysis.or(Collections.singletonList(
                    QueryAnalysis.simple(Collections.singleton("__norPresent"), Collections.emptySet())
            ));
        }
        
        // Simple query without logical operators
        return analyzeSimpleDocument(document);
    }
    
    /**
     * Handles $or operator.
     */
    @SuppressWarnings("unchecked")
    private QueryAnalysis handleOrOperator(Document document) {
        List<Document> orClauses = (List<Document>) document.get("$or");
        List<QueryAnalysis> branches = new ArrayList<>();
        
        for (Document clause : orClauses) {
            branches.add(analyzeSimpleDocument(clause)); // Use analyzeSimpleDocument for OR branches
        }
        
        // Check if there are other conditions at the same level
        Document remainingConditions = new Document(document);
        remainingConditions.remove("$or");
        
        if (!remainingConditions.isEmpty()) {
            // Need to apply DNF transformation - distribute remaining conditions to each branch
            List<QueryAnalysis> dnfBranches = new ArrayList<>();
            QueryAnalysis baseAnalysis = analyzeSimpleDocument(remainingConditions);
            
            for (QueryAnalysis branch : branches) {
                // Merge base conditions with each OR branch
                QueryAnalysis.Builder builder = new QueryAnalysis.Builder()
                        .addEqualityFields(branch.getEqualityFields())
                        .addRangeFields(branch.getRangeFields())
                        .addEqualityFields(baseAnalysis.getEqualityFields())
                        .addRangeFields(baseAnalysis.getRangeFields());
                dnfBranches.add(builder.build());
            }
            
            return QueryAnalysis.or(dnfBranches);
        }
        
        return QueryAnalysis.or(branches);
    }
    
    /**
     * Handles $and operator.
     */
    @SuppressWarnings("unchecked")
    private QueryAnalysis handleAndOperator(Document document) {
        List<Document> andClauses = (List<Document>) document.get("$and");
        
        Set<String> equalityFields = new HashSet<>();
        Set<String> rangeFields = new HashSet<>();
        List<QueryAnalysis> orBranches = new ArrayList<>();
        boolean hasOr = false;
        
        for (Document clause : andClauses) {
            QueryAnalysis clauseAnalysis = analyzeCriteriaDocument(clause);
            
            if (clauseAnalysis.hasOr()) {
                hasOr = true;
                orBranches.addAll(clauseAnalysis.getOrBranches());
            } else {
                equalityFields.addAll(clauseAnalysis.getEqualityFields());
                rangeFields.addAll(clauseAnalysis.getRangeFields());
            }
        }
        
        if (hasOr) {
            // Complex AND with OR inside - needs DNF transformation
            return QueryAnalysis.or(orBranches);
        }
        
        return QueryAnalysis.simple(equalityFields, rangeFields);
    }
    
    /**
     * Analyzes a simple document without logical operators.
     */
    private QueryAnalysis analyzeSimpleDocument(Document document) {
        Set<String> equalityFields = new HashSet<>();
        Set<String> rangeFields = new HashSet<>();
        
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            
            analyzeFieldCondition(field, value, equalityFields, rangeFields);
        }
        
        return QueryAnalysis.simple(equalityFields, rangeFields);
    }
    
    /**
     * Analyzes a single field condition.
     */
    private void analyzeFieldCondition(String field, Object value, 
                                       Set<String> equalityFields, Set<String> rangeFields) {
        if (value instanceof Document) {
            Document valueDoc = (Document) value;
            
            // Check for operators
            for (String operator : valueDoc.keySet()) {
                if (OperatorAnalyzer.isRangeOperator(operator)) {
                    rangeFields.add(field);
                } else if (OperatorAnalyzer.isEqualityOperator(operator)) {
                    equalityFields.add(field);
                } else if ("$not".equals(operator)) {
                    // Phase 3 enhancement: Advanced $not analysis
                    Object notValue = valueDoc.get("$not");
                    OperatorAnalyzer.FieldType notType = OperatorAnalyzer.analyze$NotOperator(notValue);
                    
                    switch (notType) {
                        case EQUALITY:
                            equalityFields.add(field);
                            break;
                        case RANGE:
                            rangeFields.add(field);
                            break;
                        case COMPLEX:
                            // Complex $not operations may not use indexes efficiently
                            // For conservative analysis, treat as range (less efficient)
                            rangeFields.add(field);
                            break;
                    }
                } else if ("$elemMatch".equals(operator)) {
                    // Phase 3 enhancement: Advanced $elemMatch analysis
                    Object elemMatchValue = valueDoc.get("$elemMatch");
                    OperatorAnalyzer.ElemMatchAnalysis analysis = 
                            OperatorAnalyzer.analyze$ElemMatchOperator(field, elemMatchValue);
                    
                    equalityFields.addAll(analysis.getEqualityFields());
                    rangeFields.addAll(analysis.getRangeFields());
                    
                    // Complex fields within $elemMatch are treated as range fields for conservative analysis
                    rangeFields.addAll(analysis.getComplexFields());
                } else {
                    // Phase 3 enhancement: Check if operator is B-tree efficient
                    Object operatorValue = valueDoc.get(operator);
                    if (OperatorAnalyzer.isBTreeEfficient(operator, operatorValue)) {
                        if (OperatorAnalyzer.isRangeOperator(operator)) {
                            rangeFields.add(field);
                        } else {
                            equalityFields.add(field);
                        }
                    } else {
                        // Non-B-tree efficient operators treated as range for conservative analysis
                        rangeFields.add(field);
                    }
                }
            }
        } else {
            // Direct value assignment is equality
            equalityFields.add(field);
        }
    }
}