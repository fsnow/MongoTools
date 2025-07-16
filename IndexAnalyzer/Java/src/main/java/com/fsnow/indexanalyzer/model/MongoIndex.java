package com.fsnow.indexanalyzer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a MongoDB index with its fields and metadata.
 */
public class MongoIndex {
    private final String name;
    private final List<IndexField> fields;
    private final boolean isCompound;
    
    public MongoIndex(String name, List<IndexField> fields) {
        this.name = Objects.requireNonNull(name, "Index name cannot be null");
        this.fields = new ArrayList<>(Objects.requireNonNull(fields, "Index fields cannot be null"));
        this.isCompound = fields.size() > 1;
    }
    
    public String getName() {
        return name;
    }
    
    public List<IndexField> getFields() {
        return Collections.unmodifiableList(fields);
    }
    
    public boolean isCompound() {
        return isCompound;
    }
    
    /**
     * Checks if this index covers a specific field.
     */
    public boolean coversField(String fieldName) {
        return fields.stream().anyMatch(f -> f.getField().equals(fieldName));
    }
    
    /**
     * Gets the position of a field in the index, or -1 if not found.
     */
    public int getFieldPosition(String fieldName) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).getField().equals(fieldName)) {
                return i;
            }
        }
        return -1;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MongoIndex that = (MongoIndex) o;
        return Objects.equals(name, that.name) && Objects.equals(fields, that.fields);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, fields);
    }
    
    @Override
    public String toString() {
        return String.format("MongoIndex{name='%s', fields=%s, compound=%s}", 
                name, fields, isCompound);
    }
}