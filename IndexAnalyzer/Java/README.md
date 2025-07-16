# MongoDB Index Analyzer - Java Implementation

A Java library that determines if Spring Data MongoDB queries can be executed using only B-tree indexes, without requiring in-memory operations. This tool helps identify potentially inefficient queries before they impact production performance.

The analysis is based on the **Equality-Sort-Range (ESR)** rule, a fundamental principle for understanding how MongoDB uses compound indexes.

## Features

-   **Spring Data `Criteria` Analysis**: Directly parses `org.springframework.data.mongodb.core.query.Criteria` and `org.springframework.data.domain.Sort` objects.
-   **Comprehensive Operator Support**:
    -   **Equality**: `is`, `in`, `eq`
    -   **Range**: `gt`, `gte`, `lt`, `lte`
    -   **Logical**: `$and`, `$or`, `$nor` (with conservative analysis for `$nor`)
    -   **Element**: `$elemMatch`
    -   **Negation**: `$not`
-   **Disjunctive Normal Form (DNF) Transformation**: Automatically transforms complex queries with nested `$or` and `$and` operators into DNF. This allows for accurate analysis of each logical branch of a query.
-   **Advanced ESR Matching**:
    -   Correctly applies the Equality-Sort-Range (ESR) rule across all query branches.
    -   Validates that all equality predicates form a prefix of the index.
    -   Supports multi-field sorts.
    -   Detects when an index can be used in **reverse order** to satisfy a sort requirement.
-   **MongoDB Integration**: Fetches index information directly from a running MongoDB instance to ensure analysis is based on the actual database state.
-   **Exception Handling**: Provides specific exceptions for common issues like invalid MongoDB namespaces or query parsing failures.

## Getting Started

### Prerequisites

-   Java 11+
-   Maven 3.6+

### Installation

You can build the project from source and install it into your local Maven repository:

```bash
# Navigate to the Java directory
cd Java

# Clean, build, and install
mvn clean install
```

Then, add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.fsnow</groupId>
    <artifactId>mongodb-index-analyzer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

The main entry point is the `IndexAnalyzer` class. It is `Closeable` and should be used within a try-with-resources block to ensure the MongoDB connection is managed correctly.

```java
import com.fsnow.indexanalyzer.IndexAnalyzer;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.domain.Sort;

public class Main {
    public static void main(String[] args) {
        String connectionString = "mongodb://localhost:27017";
        String namespace = "mydb.mycollection";

        try (IndexAnalyzer analyzer = new IndexAnalyzer(connectionString)) {
            // Example 1: A query perfectly covered by an index { "status": 1, "createdAt": -1 }
            Criteria criteria = Criteria.where("status").is("active");
            Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

            boolean isCovered = analyzer.analyzeIndexCoverage(criteria, sort, namespace);
            System.out.println("Query 1 is covered: " + isCovered); // Expected: true

            // Example 2: A complex query requiring DNF transformation
            Criteria complexCriteria = new Criteria().andOperator(
                Criteria.where("status").is("active"),
                new Criteria().orOperator(
                    Criteria.where("category").is("A"),
                    Criteria.where("score").gte(100)
                )
            );
            // This transforms to: (status = "active" AND category = "A") OR (status = "active" AND score >= 100)
            // Both branches must be covered by indexes for the result to be true.

            boolean isComplexCovered = analyzer.analyzeIndexCoverage(complexCriteria, null, namespace);
            System.out.println("Query 2 is covered: " + isComplexCovered);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## Running Tests

The project includes a comprehensive test suite using JUnit 5, Mockito, and Testcontainers.

```bash
# Run all tests from the Java directory
mvn test

# Run a specific test class
mvn test -Dtest=CompoundQueryMatchingTest
```

## Architecture

-   **Immutable Models**: Data models (e.g., `MongoIndex`, `QueryAnalysis`) are immutable to ensure thread safety.
-   **Component-Based Parsing**: `CriteriaParser`, `SortParser`, and `DNFTransformer` handle distinct stages of the analysis pipeline.
-   **Advanced Matching Logic**: The `CompoundQueryMatcher` contains the sophisticated logic for evaluating ESR rules across DNF branches, including sort order validation and reverse traversal checks.
-   **Logging**: Uses SLF4J/Logback for detailed logging, which is helpful for debugging complex query analyses.

## Dependencies

-   Spring Data MongoDB 4.2.0
-   MongoDB Java Driver 4.11.0
-   JUnit 5.10.0
-   Testcontainers 1.19.0
-   Mockito 5.5.0
-   SLF4J / Logback
