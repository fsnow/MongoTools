# MongoDB Index Analyzer - Java Implementation

A Java port of the MongoDB Index Analyzer that determines if MongoDB queries can be executed using only B-tree indexes without in-memory operations.

## Phase 1 Complete ✅

### What's Implemented

1. **Maven Project Structure**
   - Complete project setup with all dependencies
   - Organized package structure following best practices

2. **Core Data Models**
   - `QueryAnalysis` - Represents analyzed query with equality/range fields
   - `SortField` - Sort specification representation
   - `IndexField` - Index field with direction
   - `MongoIndex` - Complete index metadata
   - `FieldType` and `SortDirection` enums

3. **Criteria Parser**
   - Extracts field conditions from Spring Data MongoDB `Criteria` objects
   - Supports equality operators (`is`, `in`, `eq`)
   - Supports range operators (`gt`, `gte`, `lt`, `lte`)
   - Basic support for logical operators (`$and`, `$or`, `$nor`)
   - Special operator handling (`$not`, `$elemMatch`)

4. **MongoDB Integration**
   - `MongoClientAdapter` - Connection management
   - `IndexRetriever` - Fetches indexes from collections
   - Namespace parsing and validation

5. **Exception Handling**
   - Base `IndexAnalyzerException`
   - `InvalidNamespaceException` for namespace errors
   - `CriteriaParsingException` for parsing failures

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=IndexAnalyzerBasicTest
```

### Basic Usage (Placeholder)

```java
import com.fsnow.indexanalyzer.IndexAnalyzer;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.domain.Sort;

try (IndexAnalyzer analyzer = new IndexAnalyzer("mongodb://localhost:27017")) {
    Criteria criteria = Criteria.where("userId").is(1)
                               .and("status").is("active");
    Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
    
    boolean hasPerfectMatch = analyzer.analyzeIndexCoverage(
        criteria, 
        sort, 
        "mydb.users"
    );
    
    System.out.println("Perfect index match: " + hasPerfectMatch);
}
```

## Next Steps - Phase 2

1. **DNF Transformation**
   - Convert complex nested OR/AND structures to Disjunctive Normal Form
   - Handle cross product generation for multiple OR conditions
   - Implement recursive transformation for deep nesting

2. **Logical Operator Handling**
   - Complete implementation of `$and`, `$or`, `$nor`
   - Handle nested logical operators
   - Support for mixed AND/OR conditions

3. **Initial ESR Matching**
   - Basic Equality-Sort-Range pattern matching
   - Simple index coverage checks

## Architecture Notes

- **Reflection Usage**: The `CriteriaParser` uses `getCriteriaObject()` to access the internal MongoDB Document representation
- **Immutable Models**: All data models use immutable patterns with builders where appropriate
- **Logging**: Comprehensive logging with SLF4J/Logback for debugging
- **Thread Safety**: Designed with thread safety in mind for production use

## Dependencies

- Spring Data MongoDB 4.2.0
- MongoDB Java Driver 4.11.0
- JUnit 5.10.0 for testing
- SLF4J/Logback for logging
- Java 11+