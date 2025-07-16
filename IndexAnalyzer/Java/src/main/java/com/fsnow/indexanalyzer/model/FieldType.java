package com.fsnow.indexanalyzer.model;

/**
 * Represents the type of field condition in a MongoDB query.
 */
public enum FieldType {
    /**
     * Equality conditions: direct value assignment, $eq, $in
     */
    EQUALITY,
    
    /**
     * Range conditions: $gt, $gte, $lt, $lte
     */
    RANGE
}