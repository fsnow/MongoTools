# Plan: Enhance Java Index Analyzer Test Coverage

To: Software Engineering Agent
From: Gemini Agent
Date: 2025-07-07
Re: Porting Missing Test Cases from Javascript to Java Implementation

## 1. Objective

The goal is to improve the test coverage of the Java Index Analyzer by porting specific, valuable test cases that currently only exist in the Javascript test suite (`test-index-analyzer.js`).

The primary areas for enhancement are:
1.  **Complex DNF (Disjunctive Normal Form) Transformation Scenarios.**
2.  **Invalid Namespace Input Validation.**

## 2. DNF Transformation Enhancement

The Javascript test suite includes several complex DNF tests that are not present in the Java `DNFTransformationTest.java`. These tests involve multiple, nested, and cross-product logical operators.

**File to Modify:** `Java/src/test/java/com/fsnow/indexanalyzer/DNFTransformationTest.java`

### Tests to Add:

**Test Case 1: Triple OR Cross-Product**
*   **Description:** This test ensures that the DNF transformation correctly handles the exponential expansion of three separate `$or` clauses within an `$and`. This results in 2x2x2 = 8 DNF branches.
*   **Javascript Query:**
    ```javascript
    {
        $and: [
            { $or: [{ status: "active" }, { status: "inactive" }] },
            { $or: [{ category: "premium" }, { category: "basic" }] },
            { $or: [{ tags: "a" }, { tags: "b" }] }
        ]
    }
    ```
*   **Expected Java Outcome:** The `DNFTransformer` should produce a list of 8 `DNFBranch` objects. Each branch should be a valid combination of the terms, for example:
    *   `{ status: "active", category: "premium", tags: "a" }`
    *   `{ status: "active", category: "premium", tags: "b" }`
    *   ...and so on for all 8 combinations.
*   **Action:** Create a new `@Test` method in `DNFTransformationTest.java` that builds this query and asserts that the resulting DNF structure has 8 branches and that the content of the branches is correct.

**Test Case 2: Deeply Nested Logical Operators**
*   **Description:** This test validates the DNF transformation logic for a query with multiple levels of nested `$or` and `$and` operators.
*   **Javascript Query:**
    ```javascript
    {
        status: "active",
        $or: [
            { userId: 1 },
            {
                $and: [
                    { category: "premium" },
                    { $or: [{ score: { $gte: 90 } }, { tags: "a" }] }
                ]
            }
        ]
    }
    ```
*   **Expected Java Outcome:** The DNF transformation should result in three branches:
    1.  `{ status: "active", userId: 1 }`
    2.  `{ status: "active", category: "premium", score: { $gte: 90 } }`
    3.  `{ status: "active", category: "premium", tags: "a" }`
*   **Action:** Create a new `@Test` method in `DNFTransformationTest.java` for this scenario. Assert that the transformation produces 3 branches with the correct fields in each.

## 3. Input Validation Enhancement

The Javascript test suite includes a test for invalid namespace formatting, which is a good practice for ensuring robust input handling.

**File to Modify:** `Java/src/test/java/com/fsnow/indexanalyzer/IndexAnalyzerBasicTest.java` (or a new, dedicated test class for validation).

### Test to Add:

**Test Case: Invalid Namespace Format**
*   **Description:** This test ensures that the `IndexAnalyzer` throws an appropriate exception when provided with a malformed namespace string.
*   **Javascript Logic:**
    ```javascript
    {
        name: "Invalid namespace",
        query: { userId: 1 },
        sort: {},
        expected: "error",
        namespace: "invalid_namespace", // Does not contain a '.'
        reason: "Should throw error for invalid namespace format"
    }
    ```
*   **Expected Java Outcome:** A call to the `analyzeIndexCoverage` method with a namespace like `"invalid_namespace"` should throw an `InvalidNamespaceException` (or a similar custom exception).
*   **Action:** Add a new `@Test` method to `IndexAnalyzerBasicTest.java`. Use `assertThrows` or a similar JUnit 5 mechanism to verify that the correct exception is thrown when the `analyzeIndexCoverage` method is called with a namespace string that does not conform to the `"database.collection"` format.
    ```java
    @Test
    void testInvalidNamespaceShouldThrowException() {
        IndexAnalyzer analyzer = new IndexAnalyzer(...);
        assertThrows(InvalidNamespaceException.class, () -> {
            analyzer.analyzeIndexCoverage("invalid_namespace", new Document(), new Document());
        });
    }
    ```
Please proceed with implementing these tests in the Java test suite to achieve parity with the Javascript test coverage in these specific areas.
