# MongoDB Index Analyzer (JavaScript)

This script provides functionality to analyze MongoDB queries to determine if they are perfectly covered by existing indexes. This is useful for identifying potentially inefficient queries that may result in collection scans or in-memory sorts.

## `index-analyzer.js`

### Functionality

The core of the analyzer is the `analyzeIndexCoverage(query, sort, namespace)` function.

*   **`query`**: The MongoDB query document (e.g., `{ status: "active", score: { $gte: 90 } }`).
*   **`sort`**: The MongoDB sort document (e.g., `{ createdAt: -1 }`).
*   **`namespace`**: A string representing the database and collection (e.g., `"mydatabase.mycollection"`).

The function returns `true` if a perfect index match is found, and `false` otherwise.

### Key Features

*   **Disjunctive Normal Form (DNF) Transformation**: The script can transform complex queries with `$or` and `$and` operators into DNF. This allows for the analysis of each branch of an `$or` query independently.
*   **ESR (Equality, Sort, Range) Rule**: The analysis follows the ESR rule to determine if an index can be used effectively.
    1.  **Equality**: Fields with equality-based conditions (`$eq`, `$in`).
    2.  **Sort**: Fields used for sorting.
    3.  **Range**: Fields with range-based conditions (`$gt`, `$gte`, `$lt`, `$lte`).
*   **Advanced Operator Handling**: The script has basic handling for operators like `$not`, `$nor`, and `$elemMatch`, generally taking a conservative approach by flagging them as not perfectly covered due to their complexity.
*   **Reverse Index Traversal**: The analyzer can detect when an index can be used in reverse to satisfy a sort order.

## `test-index-analyzer.js`

### Functionality

This file contains a comprehensive test suite for the `index-analyzer.js` script. It uses the `mongo` shell's `load()` function to execute the tests.

### Test Categories

The test suite is organized into the following categories:

*   **Equality-Only Queries**: Tests for simple queries with only equality conditions.
*   **ESR Scenarios**: A wide range of tests covering the Equality-Sort-Range rule, including various combinations of equality, sort, and range predicates.
*   **OR Queries**: Tests for queries involving the `$or` operator, including scenarios where DNF transformation is required.
*   **DNF Transformation**: Specific tests to validate the correctness of the DNF transformation logic for complex queries.
*   **Advanced Operators**: Tests for queries using `$nor`, `$not`, and `$elemMatch`.
*   **Edge Cases**: Tests for scenarios like empty queries, sorts on non-existent fields, and invalid input.

### Running the Tests

The tests are designed to be run from the `mongo` shell:

```sh
mongo test-index-analyzer.js
```

The test script will:
1.  Set up a temporary test database and collection (`indexAnalyzerTest.testCollection`).
2.  Create a variety of indexes to test against.
3.  Run all the test cases and print the results.
4.  Clean up by dropping the test database.
