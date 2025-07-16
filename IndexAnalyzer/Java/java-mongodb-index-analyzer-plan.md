# Java MongoDB Index Analyzer - Project Structure Plan

## Project Overview

**Target**: Convert the JavaScript MongoDB Index Analyzer to Java, accepting `org.springframework.data.mongodb.core.query.Criteria` objects as input.

**Core Functionality**: 
- Analyze if MongoDB queries can use B-tree indexes without in-memory operations
- Support DNF transformation for complex OR queries
- Implement ESR (Equality-Sort-Range) pattern matching with reverse index traversal
- Handle advanced operators ($nor, $not, $elemMatch)

## 1. Project Structure & Package Organization

```
src/
├── main/java/com/mongodb/indexanalyzer/
│   ├── IndexAnalyzer.java                    // Main entry point
│   ├── model/
│   │   ├── QueryAnalysis.java                // Query analysis result
│   │   ├── SortField.java                    // Sort field representation
│   │   ├── IndexField.java                   // Index field representation
│   │   ├── MongoIndex.java                   // Index metadata
│   │   └── FieldType.java                    // Enum: EQUALITY, RANGE
│   ├── parser/
│   │   ├── CriteriaParser.java               // Parse Criteria to QueryAnalysis
│   │   ├── SortParser.java                   // Parse Sort to SortField[]
│   │   └── OperatorAnalyzer.java             // Analyze MongoDB operators
│   ├── transformation/
│   │   ├── DNFTransformer.java               // Transform to Disjunctive Normal Form
│   │   ├── DNFBranch.java                    // Represents DNF branch
│   │   └── LogicalOperatorHandler.java       // Handle $and, $or, $nor
│   ├── matching/
│   │   ├── ESRMatcher.java                   // ESR pattern matching
│   │   ├── IndexMatcher.java                 // Core index matching logic
│   │   └── ReverseTraversalValidator.java    // Validate reverse traversal rules
│   ├── integration/
│   │   ├── MongoClientAdapter.java           // MongoDB connection wrapper
│   │   └── IndexRetriever.java               // Retrieve indexes from MongoDB
│   └── exception/
│       ├── IndexAnalyzerException.java       // Base exception
│       ├── InvalidNamespaceException.java    // Invalid database.collection
│       └── CriteriaParsingException.java     // Criteria parsing errors
├── test/java/com/mongodb/indexanalyzer/
│   ├── IndexAnalyzerTest.java                // Main integration tests
│   ├── model/
│   ├── parser/
│   ├── transformation/
│   ├── matching/
│   └── testutil/
│       ├── TestDataGenerator.java            // Generate test data
│       ├── CriteriaTestHelper.java           // Criteria building helpers
│       └── MongoTestContainer.java           // Testcontainers setup
└── resources/
    ├── application.yml                       // Configuration
    └── test-indexes.json                     // Test index definitions
```

## 2. Key Data Models

### QueryAnalysis.java
```java
public class QueryAnalysis {
    private Set<String> equalityFields;
    private Set<String> rangeFields;
    private boolean hasOr;
    private List<QueryAnalysis> orBranches;
    // Constructor, getters, setters, builder pattern
}
```

### SortField.java
```java
public class SortField {
    private String field;
    private SortDirection direction; // ASC, DESC enum
}
```

### MongoIndex.java
```java
public class MongoIndex {
    private String name;
    private List<IndexField> fields;
    private boolean isCompound;
}
```

## 3. Core Algorithm Mapping

### Criteria → JavaScript Query Mapping

| Spring Criteria Method | JavaScript Equivalent | Field Type |
|------------------------|----------------------|------------|
| `.is(value)` | `{field: value}` | EQUALITY |
| `.in(values...)` | `{field: {$in: [...]}}` | EQUALITY |
| `.gt(value)` | `{field: {$gt: value}}` | RANGE |
| `.gte(value)` | `{field: {$gte: value}}` | RANGE |
| `.lt(value)` | `{field: {$lt: value}}` | RANGE |
| `.lte(value)` | `{field: {$lte: value}}` | RANGE |
| `.orOperator(criteria...)` | `{$or: [...]}` | OR_BRANCH |
| `.andOperator(criteria...)` | `{$and: [...]}` | AND_BRANCH |
| `.norOperator(criteria...)` | `{$nor: [...]}` | NOR_BRANCH |
| `.not()` | `{$not: {...}}` | NEGATION |
| `.elemMatch(criteria)` | `{$elemMatch: {...}}` | ARRAY_MATCH |

## 4. Implementation Strategy

### Phase 1: Core Infrastructure
1. **CriteriaParser**: Extract field conditions from Criteria objects
2. **Basic Models**: QueryAnalysis, SortField, IndexField, MongoIndex
3. **IndexRetriever**: Connect to MongoDB and fetch index metadata

### Phase 2: DNF Transformation
1. **DNFTransformer**: Convert complex OR/AND structures to DNF
2. **LogicalOperatorHandler**: Handle nested logical operators
3. **Cross Product Generation**: Implement (A OR B) AND (C OR D) expansion

### Phase 3: ESR Matching
1. **ESRMatcher**: Implement Equality-Sort-Range matching algorithm
2. **ReverseTraversalValidator**: Check reverse traversal constraints
3. **IndexMatcher**: Core perfect match detection

### Phase 4: Advanced Features
1. **Advanced Operators**: $nor, $not, $elemMatch handling
2. **Edge Cases**: Empty queries, sort-only, complex nested structures
3. **Performance Optimization**: Caching, batch processing

## 5. Technology Stack

### Dependencies
```xml
<dependencies>
    <!-- Spring Data MongoDB -->
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-mongodb</artifactId>
        <version>4.2.0</version>
    </dependency>
    
    <!-- MongoDB Java Driver -->
    <dependency>
        <groupId>org.mongodb</groupId>
        <artifactId>mongodb-driver-sync</artifactId>
        <version>4.11.0</version>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mongodb</artifactId>
        <version>1.19.0</version>
        <scope>test</scope>
    </dependency>
    
    <!-- Utilities -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
        <version>3.13.0</version>
    </dependency>
</dependencies>
```

## 6. Test Strategy

### Test Categories (Mirror JavaScript Tests)
1. **Equality-Only Tests (10)**: Single/multi-field, $in, $eq operators
2. **ESR Scenario Tests (21)**: Perfect ESR, equality+sort, range queries
3. **OR Query Tests (10)**: Simple OR, nested OR, DNF transformation
4. **DNF Tests (15)**: Cross products, deep nesting, exponential expansion
5. **Advanced Operator Tests (12)**: $nor, $not, $elemMatch
6. **Edge Case Tests (9)**: Empty queries, invalid namespaces

### Test Infrastructure
```java
@ExtendWith(MockitoExtension.class)
@Testcontainers
class IndexAnalyzerTest {
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");
    
    @Test
    void testEqualityOnlyQueries() {
        // Mirror JavaScript test cases
    }
}
```

## 7. Configuration & Integration

### Configuration
```java
@ConfigurationProperties("mongodb.index-analyzer")
public class IndexAnalyzerConfig {
    private String connectionString;
    private int timeoutMs = 30000;
    private boolean enableCaching = true;
}
```

### Spring Integration
```java
@Component
public class IndexAnalyzer {
    public boolean analyzeIndexCoverage(
        Criteria criteria, 
        Sort sort, 
        String namespace
    ) {
        // Main entry point
    }
}
```

## 8. Key Design Decisions

### Criteria Introspection Strategy
- **Reflection-based access** to internal Document structure
- **Visitor pattern** for traversing complex criteria trees
- **Immutable objects** for thread safety

### Performance Considerations
- **Lazy evaluation** of DNF transformations
- **Index metadata caching** per database connection
- **Parallel branch analysis** for large OR queries

### Error Handling
- **Checked exceptions** for infrastructure errors (MongoDB connection)
- **Unchecked exceptions** for programming errors (invalid criteria)
- **Detailed error messages** with query context

## 9. Implementation Roadmap

### Milestone 1: Foundation (Week 1)
- Set up Maven project with dependencies
- Implement basic data models (QueryAnalysis, SortField, etc.)
- Create CriteriaParser with reflection-based field extraction
- Basic MongoDB connection and index retrieval

### Milestone 2: Core Algorithm (Week 2)
- Implement DNF transformation for simple cases
- Basic ESR matching without reverse traversal
- Simple equality and range query analysis
- Initial test suite (30% of JavaScript tests)

### Milestone 3: Advanced Features (Week 3)
- Complex DNF transformation with cross products
- Reverse index traversal with constraints
- Advanced operators ($nor, $not, $elemMatch)
- Complete test coverage (100% of JavaScript tests)

### Milestone 4: Production Ready (Week 4)
- Performance optimization and caching
- Error handling and logging
- Spring Boot integration
- Documentation and examples

## 10. Critical Implementation Notes

### Criteria Introspection
Spring Data MongoDB's Criteria class encapsulates query conditions in an internal Document. Accessing this requires:
1. Reflection to access private fields
2. Understanding the Document structure for different operators
3. Handling nested Criteria for logical operators

### DNF Transformation Challenges
1. **Recursive Structure**: Criteria can nest arbitrarily deep
2. **Cross Product Explosion**: (A OR B) AND (C OR D) creates 4 branches
3. **Memory Management**: Large DNF expansions need careful handling

### ESR Matching Specifics
1. **Equality Fields**: Must form complete prefix in index
2. **Sort Fields**: Must match exactly or all be reversible
3. **Range Fields**: Can appear anywhere but affect reverse traversal
4. **Constraint**: Range + Sort on same field prohibits reverse traversal

### Spring Data MongoDB Integration Points
1. **Criteria Building**: Use static imports for readability
2. **Sort Objects**: Convert Spring Sort to internal SortField
3. **MongoTemplate**: Optional integration for index retrieval
4. **Reactive Support**: Consider reactive variants for async operations

This comprehensive plan provides a clear roadmap for implementing the Java version while maintaining feature parity with the JavaScript implementation and leveraging Java's type safety and Spring ecosystem advantages.