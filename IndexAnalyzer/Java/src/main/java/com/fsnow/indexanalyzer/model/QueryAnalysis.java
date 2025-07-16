package com.fsnow.indexanalyzer.model;

import java.util.*;

/**
 * Represents the analysis result of a MongoDB query, identifying field types and OR branches.
 */
public class QueryAnalysis {
    private final Set<String> equalityFields;
    private final Set<String> rangeFields;
    private final boolean hasOr;
    private final List<QueryAnalysis> orBranches;
    
    private QueryAnalysis(Builder builder) {
        this.equalityFields = Collections.unmodifiableSet(new HashSet<>(builder.equalityFields));
        this.rangeFields = Collections.unmodifiableSet(new HashSet<>(builder.rangeFields));
        this.hasOr = builder.hasOr;
        this.orBranches = Collections.unmodifiableList(new ArrayList<>(builder.orBranches));
    }
    
    public Set<String> getEqualityFields() {
        return equalityFields;
    }
    
    public Set<String> getRangeFields() {
        return rangeFields;
    }
    
    public boolean hasOr() {
        return hasOr;
    }
    
    public List<QueryAnalysis> getOrBranches() {
        return orBranches;
    }
    
    /**
     * Checks if this analysis represents a simple query (no OR branches).
     */
    public boolean isSimple() {
        return !hasOr;
    }
    
    /**
     * Gets all fields referenced in this query (equality + range).
     */
    public Set<String> getAllFields() {
        Set<String> allFields = new HashSet<>();
        allFields.addAll(equalityFields);
        allFields.addAll(rangeFields);
        return allFields;
    }
    
    @Override
    public String toString() {
        if (hasOr) {
            return String.format("QueryAnalysis{hasOr=true, branches=%d}", orBranches.size());
        }
        return String.format("QueryAnalysis{equality=%s, range=%s}", equalityFields, rangeFields);
    }
    
    /**
     * Builder for QueryAnalysis.
     */
    public static class Builder {
        private final Set<String> equalityFields = new HashSet<>();
        private final Set<String> rangeFields = new HashSet<>();
        private boolean hasOr = false;
        private final List<QueryAnalysis> orBranches = new ArrayList<>();
        
        public Builder addEqualityField(String field) {
            equalityFields.add(field);
            return this;
        }
        
        public Builder addEqualityFields(Collection<String> fields) {
            equalityFields.addAll(fields);
            return this;
        }
        
        public Builder addRangeField(String field) {
            rangeFields.add(field);
            return this;
        }
        
        public Builder addRangeFields(Collection<String> fields) {
            rangeFields.addAll(fields);
            return this;
        }
        
        public Builder setHasOr(boolean hasOr) {
            this.hasOr = hasOr;
            return this;
        }
        
        public Builder addOrBranch(QueryAnalysis branch) {
            orBranches.add(branch);
            return this;
        }
        
        public Builder addOrBranches(Collection<QueryAnalysis> branches) {
            orBranches.addAll(branches);
            return this;
        }
        
        public QueryAnalysis build() {
            return new QueryAnalysis(this);
        }
    }
    
    /**
     * Creates a simple query analysis with no OR branches.
     */
    public static QueryAnalysis simple(Set<String> equalityFields, Set<String> rangeFields) {
        return new Builder()
                .addEqualityFields(equalityFields)
                .addRangeFields(rangeFields)
                .build();
    }
    
    /**
     * Creates an OR query analysis with multiple branches.
     */
    public static QueryAnalysis or(List<QueryAnalysis> branches) {
        return new Builder()
                .setHasOr(true)
                .addOrBranches(branches)
                .build();
    }
}