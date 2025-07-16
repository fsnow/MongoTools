package com.fsnow.indexanalyzer.parser;

import com.fsnow.indexanalyzer.model.SortDirection;
import com.fsnow.indexanalyzer.model.SortField;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Spring Data MongoDB Sort objects to extract sort fields.
 */
public class SortParser {
    
    /**
     * Parses a Sort object and returns a list of SortFields.
     */
    public List<SortField> parse(Sort sort) {
        List<SortField> sortFields = new ArrayList<>();
        
        if (sort == null || sort.isUnsorted()) {
            return sortFields;
        }
        
        for (Sort.Order order : sort) {
            String fieldName = order.getProperty();
            SortDirection direction = order.isAscending() ? SortDirection.ASC : SortDirection.DESC;
            sortFields.add(new SortField(fieldName, direction));
        }
        
        return sortFields;
    }
}