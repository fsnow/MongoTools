# MongoDB Index Analyzer

This project provides two implementations of a tool to analyze MongoDB queries and determine if they can be fully covered by existing B-tree indexes. The goal is to help developers identify queries that might perform inefficiently due to in-memory sorting or collection scans.

The analysis is based on the **Equality-Sort-Range (ESR)** rule, a fundamental principle for understanding how MongoDB uses compound indexes.

## Implementations

This repository contains two separate implementations of the Index Analyzer:

-   **[Java Implementation](./Java/README.md)**
-   **[JavaScript Implementation](./JavaScript/README.md)**

### Java Implementation

The Java version is a structured, object-oriented library built as a Maven project. It is designed to be integrated into Java applications that use Spring Data MongoDB.

-   **Environment**: Java 11+, Maven
-   **Query Language**: Spring Data MongoDB `Criteria` API
-   **Key Features**:
    -   Type-safe query building
    -   Integration with the MongoDB Java Driver
    -   Comprehensive unit tests with JUnit 5 and Testcontainers
    -   Detailed analysis of query structure

**[>> Learn more about the Java Implementation](./Java/README.md)**

### JavaScript Implementation

The JavaScript version is a lightweight script designed to be run directly within the MongoDB shell (`mongo` or `mongosh`). It is ideal for quick analysis and testing without the need for a full application setup.

-   **Environment**: MongoDB Shell
-   **Query Language**: Raw MongoDB query documents (JSON-like objects)
--   **Key Features**:
    -   No external dependencies
    -   Handles complex queries with `$or` and `$and` operators through Disjunctive Normal Form (DNF) transformation
    -   Includes an extensive, self-contained test suite

**[>> Learn more about the JavaScript Implementation](./JavaScript/README.md)**

## Comparison

| Feature                  | Java Implementation                               | JavaScript Implementation                          |
| ------------------------ | ------------------------------------------------- | -------------------------------------------------- |
| **Environment**          | Java 11+, Maven                                   | MongoDB Shell (`mongo` or `mongosh`)               |
| **Query Input**          | Spring Data `Criteria` objects                    | Raw MongoDB query documents                        |
| **Primary Use Case**     | Integrating into existing Java applications       | Ad-hoc analysis, scripting, and direct DB testing  |
| **Dependencies**         | Spring Data, MongoDB Driver, SLF4J                | None                                               |
| **Testing**              | JUnit 5, Mockito, Testcontainers                  | Self-contained test suite run in the `mongo` shell |

## Getting Started

### Java

1.  **Prerequisites**: Java 11+ and Maven 3.6+ installed.
2.  **Navigate to the `Java` directory**:
    ```sh
    cd Java
    ```
3.  **Run the tests to verify the setup**:
    ```sh
    mvn test
    ```
4.  **Integrate into your project**: Add the `mongodb-index-analyzer` as a dependency to your project (e.g., by installing it locally or deploying it to a repository). See the [Java README](./Java/README.md) for usage examples.

### JavaScript

1.  **Prerequisites**: A running MongoDB instance and the `mongo` or `mongosh` shell.
2.  **Navigate to the `JavaScript` directory**:
    ```sh
    cd JavaScript
    ```
3.  **Run the test suite from your terminal**:
    ```sh
    mongo test-index-analyzer.js
    ```
    This will connect to your local MongoDB instance, create a temporary test collection, run the analysis, and print the results.
4.  **Use in the shell**: Load the `index-analyzer.js` script in a `mongo` shell session to use the `analyzeIndexCoverage` function directly:
    ```javascript
    load('index-analyzer.js');

    const query = { status: "active" };
    const sort = { createdAt: -1 };
    const namespace = "mydatabase.mycollection"; // Replace with your namespace

    const isCovered = analyzeIndexCoverage(query, sort, namespace);
    print(`Query is covered by an index: ${isCovered}`);
    ```