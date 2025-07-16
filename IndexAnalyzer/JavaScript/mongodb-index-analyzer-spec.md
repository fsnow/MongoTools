# MongoDB Index Coverage Analyzer - Implementation Guide

This document provides comprehensive specifications for implementing a MongoDB index coverage analyzer that determines whether a query can be executed using only B-tree indexes without in-memory sorting or filtering.

## Overview

The analyzer takes three inputs:
1. **MQL Query**: MongoDB query document
2. **Sort Specification**: MongoDB sort document  
3. **Namespace**: String in format "database.collection"

It returns a **boolean**:
- `true`: Query can be executed entirely using B-tree indexes
- `false`: Query requires in-memory operations or Atlas Search

## Core Algorithm

### 1. Query Transformation (DNF)

**Purpose**: Transform complex nested queries to Disjunctive Normal Form (DNF) for easier analysis.

**DNF Examples**:
```
{a:1, $or:[{b:2}, {c:3}]} → {$or:[{a:1, b:2}, {a:1, c:3}]}
{$and:[{a:1}, {$or:[{b:2}, {c:3}]}]} → {$or:[{a:1, b:2}, {a:1, c:3}]}
```

**Algorithm**:
1. **Detection**: Check if transformation is needed:
   - OR with other conditions at same level: `{a:1, $or:[...]}`
   - OR within $and arrays: `{$and:[{a:1}, {$or:[...]}]}`
   - Nested structures requiring recursive transformation

2. **Extraction**: Convert query to list of conjunctions (AND terms):
   - **Literals**: Direct field conditions `{a:1}`
   - **OR terms**: Disjunctive branches `{$or:[{b:2}, {c:3}]}`

3. **Cross Product**: For multiple OR terms, compute all combinations:
   ```
   (A OR B) AND (C OR D) → (A∧C) OR (A∧D) OR (B∧C) OR (B∧D)
   ```

4. **Distribution**: Combine literals with each OR branch:
   ```
   Input: {status:"active", $or:[{userId:1}, {category:"premium"}]}
   Output: {$or:[{status:"active", userId:1}, {status:"active", category:"premium"}]}
   ```

5. **Recursion**: Apply transformation recursively for deep nesting

### 2. Query Analysis

**Purpose**: Extract field usage patterns from each query branch.

**Field Classification**:
- **Equality fields**: Direct values, `$eq`, `$in`
- **Range fields**: `$gt`, `$gte`, `$lt`, `$lte`

**Operators**:
```javascript
// Equality
{field: value}
{field: {$eq: value}}
{field: {$in: [values]}}

// Range  
{field: {$gte: value}}
{field: {$lt: value}}

// Other operators treated as equality for index purposes
{field: {$regex: pattern}}
{field: {$exists: true}}
```

**OR Query Handling**:
- Transform to DNF first
- Analyze each branch independently
- ALL branches must have perfect index coverage

### 3. Sort Analysis

**Purpose**: Extract sort field requirements with directions.

**Format**:
```javascript
{field1: 1, field2: -1} → [{field: "field1", direction: "asc"}, {field: "field2", direction: "desc"}]
```

### 4. ESR Index Matching

**Purpose**: Determine if an index can perfectly satisfy a query using MongoDB's ESR principle.

**ESR Principle**:
- **E**quality fields first (order doesn't matter within equality)
- **S**ort fields next (exact order and direction must match)
- **R**ange fields last (must be present somewhere in index)

**Algorithm**:
1. **Equality Matching**:
   - All equality fields must be covered by index prefix
   - Order doesn't matter among equality fields
   - Track position after last equality field

2. **Sort Matching**:
   - Remaining index fields must match sort specification exactly
   - Field names must match in exact order
   - Directions must match exactly (`1` vs `-1`) OR all directions can be reversed
   - MongoDB can traverse entire index in reverse if all sort directions are opposite
   - **Important constraint**: If any sort field is also used for range queries, reverse traversal is NOT allowed
   - Range queries require the index to be in the correct direction
   - Update position after sort fields

3. **Range Matching**:
   - All range fields must appear somewhere in the index
   - Can be before, during, or after sort fields
   - Special case: field used for both sort and range is valid

**Examples**:
```javascript
// Index: {userId: 1, status: 1, createdAt: -1, score: 1}
// Query: {userId: 1, status: "active", createdAt: {$gte: date}}
// Sort: {createdAt: -1}
// Result: PERFECT MATCH
// - Equality: userId, status (positions 0-1)
// - Sort: createdAt: -1 (position 2, matches index)
// - Range: createdAt (covered by position 2)

// Index: {status: 1, createdAt: -1}  
// Query: {status: "active"}
// Sort: {createdAt: 1}
// Result: PERFECT MATCH
// - Index can be traversed in reverse: effectively {status: -1, createdAt: 1}
// - Equality: status (position 0)
// - Sort: createdAt: 1 (position 1, satisfied by reverse traversal)

// Index: {score: -1, createdAt: 1}
// Query: {score: {$gte: 80}}
// Sort: {score: 1}
// Result: NO MATCH
// - Range and sort on same field (score) with mismatched directions
// - Reverse traversal not allowed for range+sort on same field
```

## Implementation Details

### 1. Data Structures

**Query Analysis Result**:
```typescript
interface QueryAnalysis {
    equalityFields: Set<string>
    rangeFields: Set<string>
    hasOr: boolean
    orBranches: QueryAnalysis[]  // Only populated if hasOr = true
}
```

**Sort Field**:
```typescript
interface SortField {
    field: string
    direction: "asc" | "desc"
}
```

**Index Structure**:
```typescript
interface IndexField {
    field: string
    direction: "asc" | "desc"
}

interface Index {
    name: string
    key: Record<string, 1 | -1>
    // Convert to IndexField[] for processing
}
```

### 2. DNF Transformation Implementation

**Key Functions**:

1. **needsTransformation(query)**: Boolean check for transformation requirement
2. **extractConjunctions(query)**: Convert to list of literal/OR terms
3. **computeCrossProduct(conjunctions)**: Generate all branch combinations
4. **distributeConditions(literals, orTerms)**: Combine base conditions with OR branches

**Recursive Handling**:
- Apply transformation recursively for nested structures
- Ensure literals are properly distributed to all sub-branches
- Handle edge cases like empty OR branches

### 3. Database Integration

**Connection Pattern**:
```typescript
// Pseudo-code for database operations
function analyzeIndexCoverage(query, sort, namespace) {
    const [database, collection] = namespace.split('.')
    const db = getDatabase(database)
    const coll = db.getCollection(collection)
    const indexes = coll.getIndexes()
    
    // Analysis logic...
}
```

**Index Retrieval**:
- Call database `getIndexes()` method
- Convert index documents to internal format
- Handle both simple and compound indexes

### 4. Error Handling

**Validation**:
- Namespace format: Must be "database.collection"
- Query structure: Valid MongoDB query document
- Sort specification: Valid sort document

**Edge Cases**:
- Empty queries: `{}` (should return true)
- Sort-only queries: `{}, {field: 1}` 
- Invalid operators: Treat unknown operators as equality
- Missing collections: Handle gracefully

## Test Requirements

### Test Categories (77 total tests)

1. **Equality-Only Queries (10 tests)**:
   - Single/multi-field equality (positive/negative)
   - `$in` and `$eq` operators (positive/negative)
   - Array field equality (positive/negative)

2. **ESR Scenarios (21 tests)**:
   - Perfect ESR patterns (positive/negative)
   - Equality + Sort combinations (positive/negative)  
   - Range queries (positive/negative)
   - Complex multi-field scenarios (positive/negative)
   - Same field in multiple ESR categories (positive/negative)
   - ESR ordering violations (positive/negative)
   - Multi-field sort with ESR (positive/negative)
   - Complex operators in ESR context (positive/negative)

3. **OR Queries (10 tests)**:
   - Simple OR branches (positive/negative)
   - OR with sort requirements (positive/negative)
   - Complex OR with ESR (positive/negative)
   - Mixed equality/range OR (positive/negative)
   - Nested OR transformations (positive/negative)

4. **DNF Transformation (15 tests)**:
   - Multiple OR cross products: `(A OR B) AND (C OR D)`
   - Deep nesting (3+ levels): `A AND (B OR (C AND (D OR E)))`
   - OR within $and arrays: `{$and: [A, {$or: [B, C]}, D]}`
   - Exponential expansion: `(A OR B) AND (C OR D) AND (E OR F)` → 8 branches
   - Edge cases: empty branches, single results

5. **Advanced Operators (12 tests)**:
   - $nor operator handling (conservative rejection)
   - $not operator with equality and range conditions
   - $elemMatch for array element matching with dot notation
   - Mixed advanced operators in OR/DNF contexts

6. **Edge Cases (9 tests)**:
   - Empty queries and sort-only specifications
   - Multi-field sort matching (positive/negative)
   - Query+sort on same field (positive/negative)
   - Invalid namespace handling

### Required Test Indexes

Create these indexes for comprehensive testing:
```javascript
{userId: 1}                                    // Single field
{status: 1, createdAt: -1}                    // Compound  
{userId: 1, status: 1, createdAt: -1}         // ESR pattern
{category: 1, score: 1}                       // Multi-field equality
{status: 1, category: 1, createdAt: -1, score: 1}  // Complex compound
{createdAt: -1}                               // Sort-only
{score: -1, createdAt: 1}                     // Range+sort test
{tags: 1}                                     // Array field
{status: 1, category: 1, tags: 1}            // Triple OR tests
```

### Test Data

Sample documents:
```javascript
[
  {userId: 1, status: "active", category: "premium", createdAt: ISODate("2024-01-01"), score: 95, tags: ["a", "b"]},
  {userId: 2, status: "inactive", category: "basic", createdAt: ISODate("2024-01-15"), score: 73, tags: ["c", "d"]},
  {userId: 3, status: "active", category: "premium", createdAt: ISODate("2024-02-01"), score: 88, tags: ["a", "c"]}
]
```

## Implementation Verification

### Critical Test Cases

**Verify DNF Transformation**:
```javascript
// Input
{status: "active", $or: [{userId: 1}, {category: "premium"}]}

// Expected DNF Output  
{$or: [{status: "active", userId: 1}, {status: "active", category: "premium"}]}

// Expected Result: true (both branches have indexes)
```

**Verify ESR Matching**:
```javascript
// Query: {userId: 1, status: "active", createdAt: {$gte: date}}
// Sort: {createdAt: -1}
// Index: {userId: 1, status: 1, createdAt: -1}
// Expected: true (perfect ESR match)
```

**Verify Cross Product**:
```javascript
// Query: {$and: [{$or: [{status: "active"}, {status: "inactive"}]}, {$or: [{category: "premium"}, {category: "basic"}]}]}
// Expected DNF: 4 branches (2×2 cross product)
// With proper indexes: true
```

### Performance Considerations

- **Exponential Growth**: Cross products can create many branches (2^n)
- **Recursion Depth**: Deep nesting requires careful recursion handling
- **Index Lookup**: Optimize index matching for large numbers of indexes
- **Memory Usage**: Large DNF expansions may require memory management

### Language-Specific Notes

- **JavaScript**: Use `Object.assign()` for object merging, `Set` for field tracking
- **Python**: Use `dict.update()`, `set` data structures
- **Java**: Use `HashMap.putAll()`, `HashSet` collections
- **C#**: Use `Dictionary`, `HashSet` collections
- **Go**: Use maps and custom set implementations

This specification should provide sufficient detail to implement the MongoDB index coverage analyzer in any programming language while maintaining the exact same logic and test coverage as the JavaScript implementation.