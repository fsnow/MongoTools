package com.fsnow.indexanalyzer.matching;

import com.fsnow.indexanalyzer.model.MongoIndex;
import com.fsnow.indexanalyzer.model.QueryAnalysis;
import com.fsnow.indexanalyzer.model.SortField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Core index matching logic to determine if an index provides perfect coverage.
 * Phase 3 enhancement: Uses CompoundQueryMatcher for advanced sort and query analysis.
 */
public class IndexMatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(IndexMatcher.class);
    
    private final ESRMatcher esrMatcher;
    private final CompoundQueryMatcher compoundMatcher;
    
    public IndexMatcher() {
        this.esrMatcher = new ESRMatcher();
        this.compoundMatcher = new CompoundQueryMatcher();
    }
    
    /**
     * Determines if any index provides perfect match for the query and sort.
     * Phase 3 enhancement: Delegates to CompoundQueryMatcher for comprehensive analysis.
     */
    public boolean hasIndexPerfectMatch(QueryAnalysis queryAnalysis, 
                                        List<SortField> sortFields, 
                                        List<MongoIndex> indexes) {
        // Check for $nor presence - conservative rejection
        if (queryAnalysis.getEqualityFields().contains("__norPresent")) {
            logger.debug("Query contains $nor, rejecting for conservative analysis");
            return false;
        }
        
        // Use the enhanced compound query matcher for comprehensive analysis
        return compoundMatcher.hasIndexPerfectMatch(queryAnalysis, sortFields, indexes);
    }
    
    /**
     * Legacy method for backward compatibility.
     * Checks if a specific index perfectly matches a query using basic ESR logic.
     */
    public boolean indexPerfectlyMatches(QueryAnalysis queryAnalysis, 
                                       List<SortField> sortFields, 
                                       MongoIndex index) {
        return esrMatcher.indexPerfectlyMatches(queryAnalysis, sortFields, index);
    }
}