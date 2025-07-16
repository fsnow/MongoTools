package com.fsnow.indexanalyzer.model;

/**
 * Represents the sort direction for a field.
 */
public enum SortDirection {
    ASC(1),
    DESC(-1);
    
    private final int value;
    
    SortDirection(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public static SortDirection fromValue(int value) {
        return value >= 0 ? ASC : DESC;
    }
    
    /**
     * Returns the opposite direction.
     */
    public SortDirection reverse() {
        return this == ASC ? DESC : ASC;
    }
}