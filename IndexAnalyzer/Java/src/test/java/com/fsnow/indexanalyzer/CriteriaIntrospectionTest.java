package com.fsnow.indexanalyzer;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

import java.lang.reflect.Field;

/**
 * Test to understand the internal structure of Criteria class.
 */
public class CriteriaIntrospectionTest {
    
    @Test
    void introspectCriteriaClass() {
        Criteria criteria = Criteria.where("userId").is(1);
        
        System.out.println("Criteria class: " + criteria.getClass().getName());
        System.out.println("\nDeclared fields:");
        
        Field[] fields = Criteria.class.getDeclaredFields();
        for (Field field : fields) {
            System.out.println("  - " + field.getName() + " : " + field.getType().getSimpleName());
        }
        
        System.out.println("\nTrying to get criteria document...");
        System.out.println("getCriteriaObject() result: " + criteria.getCriteriaObject());
    }
}