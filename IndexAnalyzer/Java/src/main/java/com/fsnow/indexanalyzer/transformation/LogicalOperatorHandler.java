package com.fsnow.indexanalyzer.transformation;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles logical operators ($and, $or, $nor) for DNF transformation.
 */
public class LogicalOperatorHandler {
    
    /**
     * Represents a conjunction term in DNF processing.
     */
    public static class Conjunction {
        public enum Type { LITERAL, OR }
        
        private final Type type;
        private final Object value;
        
        public Conjunction(Type type, Object value) {
            this.type = type;
            this.value = value;
        }
        
        public Type getType() { return type; }
        public Object getValue() { return value; }
        
        @SuppressWarnings("unchecked")
        public Document getLiteralValue() {
            if (type != Type.LITERAL) {
                throw new IllegalStateException("Not a literal conjunction");
            }
            return (Document) value;
        }
        
        @SuppressWarnings("unchecked")
        public List<Document> getOrValue() {
            if (type != Type.OR) {
                throw new IllegalStateException("Not an OR conjunction");
            }
            return (List<Document>) value;
        }
    }
    
    /**
     * Checks if a query needs DNF transformation.
     */
    public boolean needsTransformation(Document query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        
        // Check for OR with other conditions at same level
        if (query.containsKey("$or") && query.size() > 1) {
            return true;
        }
        
        // Check for OR within $and
        if (query.containsKey("$and")) {
            @SuppressWarnings("unchecked")
            List<Document> andClauses = (List<Document>) query.get("$and");
            return andClauses.stream().anyMatch(item -> item.containsKey("$or"));
        }
        
        // Check for $nor (requires De Morgan transformation)
        if (query.containsKey("$nor")) {
            return true;
        }
        
        // Check for nested structures
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            if ("$and".equals(entry.getKey()) && entry.getValue() instanceof List) {
                @SuppressWarnings("unchecked")
                List<Document> andTerms = (List<Document>) entry.getValue();
                if (andTerms.stream().anyMatch(this::needsTransformation)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Extracts conjunctions from a query for DNF processing.
     */
    public List<Conjunction> extractConjunctions(Document query) {
        List<Conjunction> conjunctions = new ArrayList<>();
        
        if (query.containsKey("$and")) {
            // Explicit $and array
            @SuppressWarnings("unchecked")
            List<Document> andTerms = (List<Document>) query.get("$and");
            for (Document term : andTerms) {
                if (term.containsKey("$or")) {
                    @SuppressWarnings("unchecked")
                    List<Document> orTerms = (List<Document>) term.get("$or");
                    conjunctions.add(new Conjunction(Conjunction.Type.OR, orTerms));
                } else {
                    conjunctions.add(new Conjunction(Conjunction.Type.LITERAL, term));
                }
            }
        } else {
            // Implicit AND at top level
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if ("$or".equals(key)) {
                    conjunctions.add(new Conjunction(Conjunction.Type.OR, value));
                } else if ("$and".equals(key)) {
                    // Nested $and - flatten it
                    @SuppressWarnings("unchecked")
                    List<Document> nestedAndTerms = (List<Document>) value;
                    for (Document term : nestedAndTerms) {
                        if (term.containsKey("$or")) {
                            @SuppressWarnings("unchecked")
                            List<Document> orTerms = (List<Document>) term.get("$or");
                            conjunctions.add(new Conjunction(Conjunction.Type.OR, orTerms));
                        } else {
                            conjunctions.add(new Conjunction(Conjunction.Type.LITERAL, term));
                        }
                    }
                } else {
                    // Regular field condition
                    Document literal = new Document(key, value);
                    conjunctions.add(new Conjunction(Conjunction.Type.LITERAL, literal));
                }
            }
        }
        
        return conjunctions;
    }
    
    /**
     * Computes the cross product of OR branches.
     */
    public List<DNFBranch> computeCrossProduct(List<Conjunction> conjunctions) {
        // Separate literals and OR terms
        List<Document> literals = new ArrayList<>();
        List<List<Document>> orTerms = new ArrayList<>();
        
        for (Conjunction conj : conjunctions) {
            if (conj.getType() == Conjunction.Type.LITERAL) {
                literals.add(conj.getLiteralValue());
            } else {
                orTerms.add(conj.getOrValue());
            }
        }
        
        // If no OR terms, return combined literals
        if (orTerms.isEmpty()) {
            Document combined = new Document();
            for (Document literal : literals) {
                combined.putAll(literal);
            }
            return List.of(new DNFBranch(combined));
        }
        
        // Start with the first OR term
        List<List<Document>> branches = new ArrayList<>();
        for (Document branch : orTerms.get(0)) {
            List<Document> singleBranch = new ArrayList<>();
            singleBranch.add(branch);
            branches.add(singleBranch);
        }
        
        // Cross product with remaining OR terms
        for (int i = 1; i < orTerms.size(); i++) {
            List<List<Document>> newBranches = new ArrayList<>();
            for (List<Document> existingBranch : branches) {
                for (Document newTerm : orTerms.get(i)) {
                    List<Document> combinedBranch = new ArrayList<>(existingBranch);
                    combinedBranch.add(newTerm);
                    newBranches.add(combinedBranch);
                }
            }
            branches = newBranches;
        }
        
        // Combine each branch with literals
        List<DNFBranch> result = new ArrayList<>();
        for (List<Document> branch : branches) {
            Document combined = new Document();
            
            // Add all literals first
            for (Document literal : literals) {
                combined.putAll(literal);
            }
            
            // Add all terms from this branch
            for (Document term : branch) {
                combined.putAll(term);
            }
            
            result.add(new DNFBranch(combined));
        }
        
        return result;
    }
}