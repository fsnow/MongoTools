// Load the main script (assumes index-analyzer.js is in same directory)
load('./index-analyzer.js');

function runIndexAnalyzerTests() {
    print("=== MongoDB Index Analyzer Test Suite (Enhanced) ===\n");
    
    // Setup test database and collection
    setupTestEnvironment();
    
    // Run test categories with enhanced coverage
    testEqualityOnlyQueries();
    testESRScenarios();
    testORQueries();
    testDNFTransformation();
    testAdvancedOperators();
    testEdgeCases();
    
    // Cleanup
    cleanupTestEnvironment();
    
    print("\n=== Test Suite Complete ===");
}

function setupTestEnvironment() {
    print("Setting up test environment...");
    
    const testDb = db.getSiblingDB('indexAnalyzerTest');
    
    // Drop existing collection if it exists
    testDb.testCollection.drop();
    
    // Create test collection with sample data
    testDb.testCollection.insertMany([
        { 
            userId: 1, status: "active", category: "premium", createdAt: new Date("2024-01-01"), score: 95, 
            tags: ["a", "b"], 
            items: [{ name: "item1", quantity: 15, price: 10.5 }, { name: "item2", quantity: 5, price: 25.0 }]
        },
        { 
            userId: 2, status: "inactive", category: "basic", createdAt: new Date("2024-01-15"), score: 73, 
            tags: ["c", "d"], 
            items: [{ name: "item3", quantity: 8, price: 15.0 }]
        },
        { 
            userId: 3, status: "active", category: "premium", createdAt: new Date("2024-02-01"), score: 88, 
            tags: ["a", "c"], 
            items: [{ name: "item4", quantity: 20, price: 8.0 }, { name: "item5", quantity: 12, price: 18.5 }]
        }
    ]);
    
    // Create comprehensive set of indexes
    testDb.testCollection.createIndex({ userId: 1 }); // Single field (redundant with compound, but kept for test completeness)
    testDb.testCollection.createIndex({ status: 1, createdAt: -1 }); // Compound
    testDb.testCollection.createIndex({ userId: 1, status: 1, createdAt: -1 }); // ESR pattern
    testDb.testCollection.createIndex({ category: 1, score: 1 }); // Multi-field equality
    testDb.testCollection.createIndex({ status: 1, category: 1, createdAt: -1, score: 1 }); // Complex compound
    testDb.testCollection.createIndex({ createdAt: -1 }); // Sort-only index
    testDb.testCollection.createIndex({ score: -1, createdAt: 1 }); // For positive range+sort test
    testDb.testCollection.createIndex({ tags: 1 }); // For array field tests
    testDb.testCollection.createIndex({ status: 1, category: 1, tags: 1 }); // For triple OR cross product tests
    testDb.testCollection.createIndex({ "items.quantity": 1 }); // For $elemMatch tests
    testDb.testCollection.createIndex({ "items.name": 1, "items.price": 1 }); // For compound $elemMatch tests
    
    print("✓ Test environment setup complete\n");
}

function cleanupTestEnvironment() {
    print("Cleaning up test environment...");
    const testDb = db.getSiblingDB('indexAnalyzerTest');
    testDb.dropDatabase();
    print("✓ Cleanup complete");
}

function testEqualityOnlyQueries() {
    print("--- Testing Equality-Only Queries ---");
    
    const tests = [
        // Single field equality - positive and negative
        {
            name: "Single field equality - exact match",
            query: { userId: 1 },
            sort: {},
            expected: true,
            reason: "userId index exists"
        },
        {
            name: "Single field equality - no matching index",
            query: { nonExistentField: 1 },
            sort: {},
            expected: false,
            reason: "No index on nonExistentField"
        },
        
        // Multi-field equality - positive and negative
        {
            name: "Multi-field equality - perfect match",
            query: { category: "premium", score: 95 },
            sort: {},
            expected: true,
            reason: "category_1_score_1 index covers both fields"
        },
        {
            name: "Multi-field equality - partial match",
            query: { userId: 1, nonExistentField: "value" },
            sort: {},
            expected: false,
            reason: "No index covers both userId and nonExistentField"
        },
        
        // $in operator - positive and negative
        {
            name: "Equality with $in operator - indexed field",
            query: { status: { $in: ["active", "inactive"] } },
            sort: {},
            expected: true,
            reason: "status field covered by multiple compound indexes"
        },
        {
            name: "Equality with $in operator - non-indexed field",
            query: { nonExistentField: { $in: ["value1", "value2"] } },
            sort: {},
            expected: false,
            reason: "No index on nonExistentField"
        },
        
        // $eq operator - positive and negative
        {
            name: "Equality with $eq operator - indexed field",
            query: { userId: { $eq: 1 } },
            sort: {},
            expected: true,
            reason: "userId index exists"
        },
        {
            name: "Equality with $eq operator - non-indexed field",
            query: { nonExistentField: { $eq: "value" } },
            sort: {},
            expected: false,
            reason: "No index on nonExistentField"
        },
        
        // Array field equality
        {
            name: "Array field equality - indexed",
            query: { tags: "a" },
            sort: {},
            expected: true,
            reason: "tags index exists"
        },
        {
            name: "Array field equality - non-indexed array",
            query: { nonExistentArray: "value" },
            sort: {},
            expected: false,
            reason: "No index on nonExistentArray"
        }
    ];
    
    runTestCase(tests, "indexAnalyzerTest.testCollection");
}

function testESRScenarios() {
    print("--- Testing ESR (Equality-Sort-Range) Scenarios ---");
    
    const tests = [
        // Perfect ESR - positive and negative
        {
            name: "Perfect ESR match",
            query: { userId: 1, status: "active", createdAt: { $gte: new Date("2024-01-01") } },
            sort: { createdAt: -1 },
            expected: true,
            reason: "userId_1_status_1_createdAt_-1 index covers E-S-R pattern"
        },
        {
            name: "ESR with wrong field order",
            query: { status: "active", userId: 1, score: { $gte: 80 } },
            sort: { createdAt: -1 },
            expected: false,
            reason: "No index with exact ESR order: status-userId-createdAt-score"
        },
        
        // Equality + Sort - positive and negative
        {
            name: "Equality + Sort only - match",
            query: { status: "active" },
            sort: { createdAt: -1 },
            expected: true,
            reason: "status_1_createdAt_-1 index covers equality and sort"
        },
        {
            name: "Equality + Sort - reverse index traversal",
            query: { status: "active" },
            sort: { createdAt: 1 },
            expected: true,
            reason: "status_1_createdAt_-1 index can be traversed in reverse for createdAt: 1 sort"
        },
        
        // Range query only - positive and negative
        {
            name: "Range query only - indexed field",
            query: { createdAt: { $gte: new Date("2024-01-01") } },
            sort: {},
            expected: true,
            reason: "createdAt_-1 index covers range query"
        },
        {
            name: "Range query only - non-indexed field",
            query: { nonExistentField: { $gte: 100 } },
            sort: {},
            expected: false,
            reason: "No index on nonExistentField for range query"
        },
        
        // Range query with sort - positive and negative
        {
            name: "Range query with sort - perfect match",
            query: { score: { $lte: 90 } },
            sort: { score: -1 },
            expected: true,
            reason: "score_-1_createdAt_1 index covers range and sort on same field"
        },
        {
            name: "Range query with sort - no matching index",
            query: { score: { $gte: 80 } },
            sort: { score: 1 },
            expected: false,
            reason: "No index with score in ascending order for sort"
        },
        
        // Multiple equality + sort - positive and negative
        {
            name: "Multiple equality fields + sort - match",
            query: { status: "active", category: "premium" },
            sort: { createdAt: -1 },
            expected: true,
            reason: "status_1_category_1_createdAt_-1_score_1 index covers pattern"
        },
        {
            name: "Multiple equality fields + sort - missing coverage",
            query: { userId: 1, category: "premium" },
            sort: { score: 1 },
            expected: false,
            reason: "No index covers userId + category equality with score sort"
        },
        
        // Complex ESR with multiple ranges - positive and negative
        {
            name: "Complex ESR with multiple range fields - match",
            query: { 
                status: "active", 
                category: "premium", 
                createdAt: { $gte: new Date("2024-01-01") },
                score: { $gte: 80 }
            },
            sort: { createdAt: -1 },
            expected: true,
            reason: "status_1_category_1_createdAt_-1_score_1 covers all fields in ESR order"
        },
        {
            name: "Complex ESR with multiple ranges - wrong order",
            query: { 
                userId: 1,
                createdAt: { $gte: new Date("2024-01-01") },
                score: { $gte: 80 }
            },
            sort: { score: 1 },
            expected: false,
            reason: "No index with proper ESR ordering for these fields"
        },
        
        // Same field in multiple ESR categories
        {
            name: "Same field range and sort - perfect match",
            query: { score: { $gte: 80 } },
            sort: { score: -1 },
            expected: true,
            reason: "score_-1_createdAt_1 index handles range and sort on same field"
        },
        {
            name: "Same field range and sort - direction mismatch", 
            query: { score: { $lte: 90 } },
            sort: { score: 1 },
            expected: false,
            reason: "score_-1_createdAt_1 index has wrong sort direction for score"
        },
        {
            name: "Same field multiple operators - complex constraints",
            query: { 
                userId: { $ne: null, $gte: 1 }
            },
            sort: {},
            expected: true,
            reason: "userId index covers multiple constraints on same field"
        },
        
        // ESR ordering violations
        {
            name: "ESR violation - true field ordering violation",
            query: { 
                score: { $gte: 80 },
                userId: 1,
                tags: "a"
            },
            sort: {},
            expected: false,
            reason: "No index has proper ESR order for score(range) + userId(equality) + tags(equality)"
        },
        {
            name: "ESR violation - missing field coverage",
            query: { 
                nonExistentField: "value",
                userId: 1
            },
            sort: { createdAt: -1 },
            expected: false,
            reason: "No index covers nonExistentField equality"
        },
        
        // Multi-field sort with ESR
        {
            name: "Multi-field sort ESR - correct order",
            query: { status: "active", category: "premium" },
            sort: { createdAt: -1, score: 1 },
            expected: true,
            reason: "status_1_category_1_createdAt_-1_score_1 matches E+E+S+S pattern"
        },
        {
            name: "Multi-field sort ESR - wrong order",
            query: { userId: 1 },
            sort: { score: 1, createdAt: -1 },
            expected: false,
            reason: "No index matches userId+score+createdAt ESR ordering"
        },
        
        // Complex operators in ESR
        {
            name: "Complex operators ESR - $in as equality",
            query: { 
                status: { $in: ["active", "pending"] },
                category: "premium",
                score: { $gte: 80 }
            },
            sort: { createdAt: -1 },
            expected: true,
            reason: "$in treated as equality, matches status_category_createdAt_score ESR pattern"
        },
        {
            name: "Complex operators ESR - mixed operators no match",
            query: {
                userId: { $ne: null },
                category: { $in: ["premium", "basic"] },
                score: { $gte: 80, $lte: 95 }
            },
            sort: { createdAt: -1 },
            expected: false,
            reason: "No index covers userId+category+createdAt+score in proper ESR order"
        }
    ];
    
    runTestCase(tests, "indexAnalyzerTest.testCollection");
}

function testORQueries() {
    print("--- Testing OR Query Scenarios ---");
    
    const tests = [
        // Simple OR - positive and negative
        {
            name: "Simple OR - both branches covered",
            query: { $or: [{ userId: 1 }, { status: "active" }] },
            sort: {},
            expected: true,
            reason: "userId index covers first branch, status indexes cover second"
        },
        {
            name: "Simple OR - one branch not covered",
            query: { $or: [{ userId: 1 }, { nonExistentField: "value" }] },
            sort: {},
            expected: false,
            reason: "Second branch has no matching index"
        },
        
        // OR with sort - positive and negative
        {
            name: "OR with sort - both branches support sort",
            query: { $or: [{ status: "active" }, { status: "inactive" }] },
            sort: { createdAt: -1 },
            expected: true,
            reason: "status_1_createdAt_-1 index covers both branches with sort"
        },
        {
            name: "OR with sort - one branch lacks sort support",
            query: { $or: [{ status: "active" }, { userId: 1 }] },
            sort: { createdAt: -1 },
            expected: false,
            reason: "userId branch doesn't have index supporting createdAt sort"
        },
        
        // Complex OR with ESR - positive and negative
        {
            name: "Complex OR with ESR - all branches covered",
            query: { 
                $or: [
                    { userId: 1, status: "active" },
                    { category: "premium", score: { $gte: 90 } }
                ]
            },
            sort: {},
            expected: true,
            reason: "Both branches have matching indexes"
        },
        {
            name: "Complex OR with ESR - missing index for branch",
            query: { 
                $or: [
                    { userId: 1, status: "active" },
                    { nonExistentField: "value", score: { $gte: 90 } }
                ]
            },
            sort: {},
            expected: false,
            reason: "Second branch has no index for nonExistentField + score range"
        },
        
        // Nested OR - positive and negative (DNF transformation)
        {
            name: "Nested OR in AND - successful DNF transformation",
            query: { 
                status: "active",
                $or: [{ userId: 1 }, { category: "premium" }]
            },
            sort: {},
            expected: true,
            reason: "After DNF transformation, both branches can use existing indexes"
        },
        {
            name: "Nested OR in AND - DNF with missing indexes",
            query: { 
                nonExistentField: "value",
                $or: [{ userId: 1 }, { category: "premium" }]
            },
            sort: {},
            expected: false,
            reason: "After DNF, no branch has index covering nonExistentField"
        },
        
        // OR with mixed equality and range
        {
            name: "OR with mixed conditions - covered",
            query: { 
                $or: [
                    { createdAt: { $gte: new Date("2024-01-01") } },
                    { score: { $lte: 90 } }
                ]
            },
            sort: {},
            expected: true,
            reason: "Both range conditions have supporting indexes"
        },
        {
            name: "OR with mixed conditions - not covered",
            query: { 
                $or: [
                    { createdAt: { $gte: new Date("2024-01-01") } },
                    { nonExistentField: { $lte: 90 } }
                ]
            },
            sort: {},
            expected: false,
            reason: "Second branch range on non-indexed field"
        }
    ];
    
    runTestCase(tests, "indexAnalyzerTest.testCollection");
}

function testDNFTransformation() {
    print("--- Testing DNF Transformation ---");
    
    const tests = [
        // 1. Multiple OR at same level - Cross Product
        {
            name: "Multiple OR at same level - both covered",
            query: { 
                $and: [
                    { $or: [{ userId: 1 }, { userId: 2 }] },
                    { $or: [{ status: "active" }, { status: "inactive" }] }
                ]
            },
            sort: {},
            expected: true,
            reason: "DNF creates 4 branches, all have userId + status indexes"
        },
        {
            name: "Multiple OR at same level - partial coverage",
            query: { 
                $and: [
                    { $or: [{ userId: 1 }, { userId: 2 }] },
                    { $or: [{ status: "active" }, { nonExistentField: "value" }] }
                ]
            },
            sort: {},
            expected: false,
            reason: "DNF creates 4 branches, 2 have nonExistentField with no index"
        },
        
        // 2. Deep nesting (3+ levels)
        {
            name: "Deep nesting - 3 levels with coverage",
            query: {
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
            },
            sort: {},
            expected: true,
            reason: "DNF expands to 3 branches: status+userId, status+category+score, status+category+tags"
        },
        {
            name: "Deep nesting - 3 levels with missing index",
            query: {
                nonExistentField: "value",
                $or: [
                    { userId: 1 },
                    { 
                        $and: [
                            { category: "premium" },
                            { $or: [{ score: { $gte: 90 } }, { tags: "a" }] }
                        ]
                    }
                ]
            },
            sort: {},
            expected: false,
            reason: "All DNF branches include nonExistentField which has no index"
        },
        
        // 3. OR within explicit $and array
        {
            name: "OR within $and array - covered",
            query: {
                $and: [
                    { status: "active" },
                    { $or: [{ userId: 1 }, { category: "premium" }] },
                    { createdAt: { $gte: new Date("2024-01-01") } }
                ]
            },
            sort: {},
            expected: true,
            reason: "DNF: (status+userId+createdAt) OR (status+category+createdAt), both covered by compound indexes"
        },
        {
            name: "OR within $and array - missing coverage",
            query: {
                $and: [
                    { status: "active" },
                    { $or: [{ userId: 1 }, { nonExistentField: "value" }] },
                    { category: "premium" }
                ]
            },
            sort: {},
            expected: false,
            reason: "Second DNF branch has nonExistentField with no index"
        },
        
        // 4. Complex mixed logical operators
        {
            name: "Mixed operators - (A OR B) AND (C OR D) - all covered",
            query: {
                $and: [
                    { $or: [{ status: "active" }, { status: "inactive" }] },
                    { $or: [{ category: "premium" }, { category: "basic" }] }
                ]
            },
            sort: { createdAt: -1 },
            expected: true,
            reason: "DNF creates 4 branches, status+category+sort covered by compound index"
        },
        {
            name: "Mixed operators - (A OR B) AND (C OR D) - partial coverage",
            query: {
                $and: [
                    { $or: [{ userId: 1 }, { userId: 2 }] },
                    { $or: [{ category: "premium" }, { nonExistentField: "value" }] }
                ]
            },
            sort: { createdAt: -1 },
            expected: false,
            reason: "Half of DNF branches have nonExistentField or lack sort support"
        },
        
        // 5. Nested OR with range queries
        {
            name: "Nested OR with ranges - ESR pattern",
            query: {
                status: "active",
                $or: [
                    { score: { $gte: 90 } },
                    { 
                        $and: [
                            { category: "premium" },
                            { createdAt: { $lte: new Date("2024-02-01") } }
                        ]
                    }
                ]
            },
            sort: { createdAt: -1 },
            expected: false,
            reason: "First branch: status+score+sort lacks proper index. Second: status+category+createdAt+sort mixed range/sort"
        },
        
        // 6. Complex OR with sort requirements
        {
            name: "Complex nested OR with sort - all branches support",
            query: {
                $or: [
                    { 
                        $and: [
                            { status: "active" },
                            { $or: [{ category: "premium" }, { category: "basic" }] }
                        ]
                    },
                    { status: "inactive" }
                ]
            },
            sort: { createdAt: -1 },
            expected: true,
            reason: "DNF: (status+category+sort) OR (status+sort), all supported by status_category_createdAt or status_createdAt indexes"
        },
        {
            name: "Complex nested OR with sort - mixed support",
            query: {
                $or: [
                    { 
                        $and: [
                            { userId: 1 },
                            { $or: [{ category: "premium" }, { score: { $gte: 90 } }] }
                        ]
                    },
                    { status: "active" }
                ]
            },
            sort: { createdAt: -1 },
            expected: false,
            reason: "First DNF branches (userId+category+sort, userId+score+sort) lack proper indexes"
        },
        
        // 7. Empty OR branches (edge case)
        {
            name: "Empty OR branch",
            query: {
                status: "active",
                $or: [{ userId: 1 }, {}]
            },
            sort: {},
            expected: true,
            reason: "DNF: (status+userId) OR (status), both covered"
        },
        
        // 8. Single branch DNF (should not create $or)
        {
            name: "Single branch DNF result",
            query: {
                status: "active",
                $or: [{ userId: 1 }]
            },
            sort: {},
            expected: true,
            reason: "DNF simplifies to single query: status+userId"
        },
        
        // 9. Quadruple cross product (2x2x2)
        {
            name: "Triple OR cross product - exponential branches",
            query: {
                $and: [
                    { $or: [{ status: "active" }, { status: "inactive" }] },
                    { $or: [{ category: "premium" }, { category: "basic" }] },
                    { $or: [{ tags: "a" }, { tags: "b" }] }
                ]
            },
            sort: {},
            expected: true,
            reason: "DNF creates 8 branches (2x2x2), all field combinations have indexes"
        },
        {
            name: "Triple OR cross product - missing indexes",
            query: {
                $and: [
                    { $or: [{ userId: 1 }, { userId: 2 }] },
                    { $or: [{ category: "premium" }, { category: "basic" }] },
                    { $or: [{ tags: "a" }, { nonExistentField: "value" }] }
                ]
            },
            sort: {},
            expected: false,
            reason: "Half of 8 DNF branches include nonExistentField"
        }
    ];
    
    runTestCase(tests, "indexAnalyzerTest.testCollection");
}

function testAdvancedOperators() {
    print("--- Testing Advanced Operators ($nor, $not, $elemMatch) ---");
    
    const tests = [
        // $nor operator tests
        {
            name: "$nor - simple negation with index coverage",
            query: { 
                $nor: [{ status: "inactive" }, { category: "basic" }],
                userId: 1
            },
            sort: {},
            expected: false,
            reason: "$nor requires complex evaluation, conservative approach returns false"
        },
        {
            name: "$nor - in OR query context",
            query: {
                $or: [
                    { userId: 1 },
                    { $nor: [{ status: "inactive" }, { score: { $lt: 50 } }] }
                ]
            },
            sort: {},
            expected: false,
            reason: "$nor in OR branch creates complex logic requiring careful index analysis"
        },
        
        // $not operator tests
        {
            name: "$not - simple field negation",
            query: { 
                status: { $not: { $eq: "inactive" } },
                category: "premium"
            },
            sort: {},
            expected: true,
            reason: "$not treated as equality constraint, status and category fields have indexes"
        },
        {
            name: "$not - with range operator",
            query: {
                userId: 1,
                score: { $not: { $lt: 80 } }
            },
            sort: {},
            expected: false,
            reason: "$not with range operators requires complex evaluation beyond simple B-tree usage"
        },
        
        // $elemMatch operator tests
        {
            name: "$elemMatch - simple array element match",
            query: { 
                items: { $elemMatch: { quantity: { $gt: 10 } } }
            },
            sort: {},
            expected: true,
            reason: "items.quantity index supports $elemMatch queries"
        },
        {
            name: "$elemMatch - compound field match",
            query: {
                status: "active",
                items: { $elemMatch: { name: "item1", price: { $lt: 20 } } }
            },
            sort: {},
            expected: false,
            reason: "Complex $elemMatch with mixed equality/range requires specialized index analysis"
        },
        {
            name: "$elemMatch - no supporting index",
            query: {
                items: { $elemMatch: { nonExistentField: "value" } }
            },
            sort: {},
            expected: false,
            reason: "No index on items.nonExistentField for $elemMatch"
        },
        {
            name: "$elemMatch - in OR query",
            query: {
                $or: [
                    { status: "active" },
                    { items: { $elemMatch: { quantity: { $gte: 15 } } } }
                ]
            },
            sort: {},
            expected: true,
            reason: "Both OR branches have index support: status index and items.quantity index"
        },
        
        // Mixed advanced operators
        {
            name: "Mixed $not and $elemMatch",
            query: {
                status: { $not: { $eq: "inactive" } },
                items: { $elemMatch: { quantity: { $gt: 5 } } }
            },
            sort: {},
            expected: false,
            reason: "Complex combination of $not and $elemMatch with range requires careful analysis"
        },
        {
            name: "Complex $elemMatch with sort",
            query: {
                items: { $elemMatch: { price: { $lt: 20 } } }
            },
            sort: { createdAt: -1 },
            expected: false,
            reason: "items.price index exists but doesn't support createdAt sort"
        },
        
        // Advanced operators in DNF context
        {
            name: "$elemMatch in nested OR (DNF transformation)",
            query: {
                status: "active",
                $or: [
                    { userId: 1 },
                    { items: { $elemMatch: { quantity: { $gte: 10 } } } }
                ]
            },
            sort: {},
            expected: false,
            reason: "Complex DNF with $elemMatch range conditions requires specialized analysis"
        },
        {
            name: "$nor with DNF transformation",
            query: {
                userId: 1,
                $nor: [{ status: "inactive" }]
            },
            sort: {},
            expected: false,
            reason: "$nor creates complex conditions that are conservatively rejected"
        }
    ];
    
    runTestCase(tests, "indexAnalyzerTest.testCollection");
}

function testEdgeCases() {
    print("--- Testing Edge Cases ---");
    
    const tests = [
        // Empty query and sort variations
        {
            name: "Empty query",
            query: {},
            sort: {},
            expected: true,
            reason: "Empty query can use any index or collection scan"
        },
        {
            name: "Only sort specification - indexed",
            query: {},
            sort: { createdAt: -1 },
            expected: true,
            reason: "createdAt_-1 index covers sort"
        },
        {
            name: "Only sort specification - non-indexed",
            query: {},
            sort: { nonExistentField: 1 },
            expected: false,
            reason: "No index on nonExistentField for sorting"
        },
        
        // Multi-field sort - positive and negative
        {
            name: "Multi-field sort - exact match",
            query: {},
            sort: { status: 1, createdAt: -1 },
            expected: true,
            reason: "status_1_createdAt_-1 index covers multi-field sort"
        },
        {
            name: "Multi-field sort - order mismatch",
            query: {},
            sort: { status: -1, createdAt: -1 },
            expected: false,
            reason: "No index matches status: -1, createdAt: -1 pattern"
        },
        {
            name: "Multi-field sort - partial match",
            query: {},
            sort: { status: 1, nonExistentField: 1 },
            expected: false,
            reason: "No index covers both status and nonExistentField"
        },
        
        
        // Query with sort on same field (edge case)
        {
            name: "Query and sort on same field - match",
            query: { createdAt: { $gte: new Date("2024-01-01") } },
            sort: { createdAt: -1 },
            expected: true,
            reason: "createdAt_-1 index handles both range and sort"
        },
        {
            name: "Query and sort on same field - direction mismatch",
            query: { createdAt: { $gte: new Date("2024-01-01") } },
            sort: { createdAt: 1 },
            expected: false,
            reason: "createdAt_-1 index has wrong sort direction"
        },
        
        // Invalid namespace
        {
            name: "Invalid namespace",
            query: { userId: 1 },
            sort: {},
            expected: "error",
            namespace: "invalid_namespace",
            reason: "Should throw error for invalid namespace format"
        }
    ];
    
    runTestCase(tests, "indexAnalyzerTest.testCollection");
}

function runTestCase(tests, defaultNamespace) {
    let passed = 0;
    let total = tests.length;
    
    tests.forEach((test, index) => {
        try {
            const namespace = test.namespace || defaultNamespace;
            const result = analyzeIndexCoverage(test.query, test.sort, namespace);
            
            let success = false;
            if (test.expected === "error") {
                print(`${index + 1}. FAIL: ${test.name}`);
                print(`   Expected error but got result: ${result}`);
            } else if (result === test.expected) {
                success = true;
                passed++;
                print(`${index + 1}. PASS: ${test.name}`);
            } else {
                print(`${index + 1}. FAIL: ${test.name}`);
                print(`   Expected: ${test.expected}, Got: ${result}`);
                print(`   Reason: ${test.reason}`);
            }
            
            if (success && test.reason) {
                print(`   ✓ ${test.reason}`);
            }
            
        } catch (error) {
            if (test.expected === "error") {
                passed++;
                print(`${index + 1}. PASS: ${test.name}`);
                print(`   ✓ Expected error: ${error.message}`);
            } else {
                print(`${index + 1}. ERROR: ${test.name}`);
                print(`   Unexpected error: ${error.message}`);
            }
        }
    });
    
    print(`\nResults: ${passed}/${total} tests passed\n`);
}

// Helper function to display current indexes (for debugging)
function showTestIndexes() {
    const testDb = db.getSiblingDB('indexAnalyzerTest');
    print("Current indexes on testCollection:");
    testDb.testCollection.getIndexes().forEach(index => {
        print(`  ${index.name}: ${JSON.stringify(index.key)}`);
    });
}

// Run the tests
runIndexAnalyzerTests();