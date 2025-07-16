package com.fsnow.indexanalyzer.transformation;

import org.bson.Document;

import java.util.Objects;

/**
 * Represents a single branch in Disjunctive Normal Form.
 * Each branch is a conjunction of conditions (AND of conditions).
 */
public class DNFBranch {
    private final Document conditions;
    
    public DNFBranch(Document conditions) {
        this.conditions = Objects.requireNonNull(conditions, "Conditions cannot be null");
    }
    
    public Document getConditions() {
        return new Document(conditions); // Return copy for immutability
    }
    
    /**
     * Merges this branch with another set of conditions.
     */
    public DNFBranch merge(Document additionalConditions) {
        Document merged = new Document(this.conditions);
        merged.putAll(additionalConditions);
        return new DNFBranch(merged);
    }
    
    /**
     * Checks if this branch is empty (no conditions).
     */
    public boolean isEmpty() {
        return conditions.isEmpty();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNFBranch dnfBranch = (DNFBranch) o;
        return Objects.equals(conditions, dnfBranch.conditions);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(conditions);
    }
    
    @Override
    public String toString() {
        return "DNFBranch{" + conditions + "}";
    }
}