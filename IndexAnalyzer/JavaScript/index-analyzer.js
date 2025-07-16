function analyzeIndexCoverage(query, sort, namespace) {
    const [database, collection] = namespace.split('.');
    
    if (!database || !collection) {
        throw new Error('Invalid namespace format. Expected: database.collection');
    }
    
    const targetDb = db.getSiblingDB(database);
    const coll = targetDb.getCollection(collection);
    
    // Get all indexes for the collection
    const indexes = coll.getIndexes();
    
    // Parse query to extract field conditions
    const queryAnalysis = parseQuery(query);
    
    // Parse sort specification
    const sortFields = parseSort(sort);
    
    // Check if any index provides perfect match
    return hasIndexPerfectMatch(queryAnalysis, sortFields, indexes);
}

function parseQuery(query) {
    // First, transform the query to disjunctive normal form if needed
    const dnfQuery = transformToDNF(query);
    
    const analysis = {
        equalityFields: new Set(),
        rangeFields: new Set(),
        hasOr: false,
        orBranches: []
    };
    
    function analyzeCondition(condition, parentContext = null) {
        const fields = {
            equality: new Set(),
            range: new Set()
        };
        
        // If we have parent context (from AND conditions), inherit those fields
        if (parentContext) {
            parentContext.equality.forEach(f => fields.equality.add(f));
            parentContext.range.forEach(f => fields.range.add(f));
        }
        
        for (const [field, value] of Object.entries(condition)) {
            if (field === '$or') {
                // At top level, OR branches are independent
                if (!parentContext) {
                    analysis.hasOr = true;
                    analysis.orBranches = value.map(branch => parseQuery(branch));
                } else {
                    // Nested OR - should have been transformed by DNF
                    throw new Error('Nested OR should have been transformed to DNF');
                }
                continue;
            }
            
            if (field === '$and') {
                for (const subCondition of value) {
                    const subFields = analyzeCondition(subCondition, fields);
                    subFields.equality.forEach(f => fields.equality.add(f));
                    subFields.range.forEach(f => fields.range.add(f));
                }
                continue;
            }
            
            if (field === '$nor') {
                // For index analysis purposes, treat negation as equality constraints
                // MongoDB will still need to examine these fields via indexes
                if (Array.isArray(value)) {
                    // $nor: [{field1: val1}, {field2: val2}] - treat each as equality
                    for (const subCondition of value) {
                        Object.keys(subCondition).forEach(f => fields.equality.add(f));
                    }
                }
                continue;
            }
            
            
            if (typeof value === 'object' && value !== null) {
                const operators = Object.keys(value);
                const hasRangeOp = operators.some(op => 
                    ['$gt', '$gte', '$lt', '$lte'].includes(op)
                );
                
                if (hasRangeOp) {
                    fields.range.add(field);
                } else if (operators.includes('$in') || operators.includes('$eq') || operators.includes('$ne')) {
                    fields.equality.add(field);
                } else if (operators.includes('$not')) {
                    // $not operator - analyze what's inside the $not
                    const notCondition = value.$not;
                    if (typeof notCondition === 'object' && notCondition !== null) {
                        const notOps = Object.keys(notCondition);
                        const hasNotRange = notOps.some(op => 
                            ['$gt', '$gte', '$lt', '$lte'].includes(op)
                        );
                        if (hasNotRange) {
                            fields.range.add(field);
                        } else {
                            fields.equality.add(field);
                        }
                    } else {
                        fields.equality.add(field);
                    }
                } else if (operators.includes('$elemMatch')) {
                    // For $elemMatch, we analyze the nested conditions and create dot notation fields
                    const elemMatchCondition = value.$elemMatch;
                    if (elemMatchCondition && typeof elemMatchCondition === 'object') {
                        for (const [nestedField, nestedValue] of Object.entries(elemMatchCondition)) {
                            // Add dot notation fields for index matching
                            const dotNotationField = `${field}.${nestedField}`;
                            if (typeof nestedValue === 'object' && nestedValue !== null) {
                                const nestedOps = Object.keys(nestedValue);
                                const hasNestedRange = nestedOps.some(op => 
                                    ['$gt', '$gte', '$lt', '$lte'].includes(op)
                                );
                                if (hasNestedRange) {
                                    fields.range.add(dotNotationField);
                                } else {
                                    fields.equality.add(dotNotationField);
                                }
                            } else {
                                fields.equality.add(dotNotationField);
                            }
                        }
                    } else {
                        // Fallback: treat as equality on the field itself
                        fields.equality.add(field);
                    }
                } else {
                    // For other operators, treat as equality for index purposes
                    fields.equality.add(field);
                }
            } else {
                // Direct value assignment is equality
                fields.equality.add(field);
            }
        }
        
        return fields;
    }
    
    // Use the DNF-transformed query
    const queryToAnalyze = dnfQuery || query;
    
    if (queryToAnalyze.$or) {
        analysis.hasOr = true;
        analysis.orBranches = queryToAnalyze.$or.map(branch => parseQuery(branch));
    } else {
        const fields = analyzeCondition(queryToAnalyze);
        analysis.equalityFields = fields.equality;
        analysis.rangeFields = fields.range;
    }
    
    return analysis;
}

// Transform query to Disjunctive Normal Form (DNF)
// Examples:
// - {a:1, $or:[{b:2}, {c:3}]} => {$or:[{a:1, b:2}, {a:1, c:3}]}
// - {$and:[{a:1}, {$or:[{b:2}, {c:3}]}]} => {$or:[{a:1, b:2}, {a:1, c:3}]}
// - Multiple OR: handled via cross product
function transformToDNF(query) {
    // Check if transformation is needed
    function needsTransformation(obj) {
        if (!obj || typeof obj !== 'object') return false;
        
        // Check for OR with other conditions at same level
        if (obj.$or && Object.keys(obj).length > 1) return true;
        
        // Check for OR within $and
        if (obj.$and && Array.isArray(obj.$and)) {
            return obj.$and.some(item => item.$or);
        }
        
        // Check for $nor (requires De Morgan transformation)
        if (obj.$nor) {
            return true;
        }
        
        // Check for nested structures
        for (const [key, value] of Object.entries(obj)) {
            if (key === '$and' && Array.isArray(value)) {
                if (value.some(item => needsTransformation(item))) return true;
            }
        }
        
        return false;
    }
    
    // Convert query to list of conjunctions (AND terms)
    function extractConjunctions(query) {
        const conjunctions = [];
        
        if (query.$and && Array.isArray(query.$and)) {
            // Explicit $and array
            for (const term of query.$and) {
                if (term.$or) {
                    conjunctions.push({ type: 'or', value: term.$or });
                } else {
                    conjunctions.push({ type: 'literal', value: term });
                }
            }
        } else {
            // Implicit AND at top level
            for (const [key, value] of Object.entries(query)) {
                if (key === '$or') {
                    conjunctions.push({ type: 'or', value: value });
                } else if (key === '$and') {
                    // Nested $and - flatten it
                    for (const term of value) {
                        if (term.$or) {
                            conjunctions.push({ type: 'or', value: term.$or });
                        } else {
                            conjunctions.push({ type: 'literal', value: term });
                        }
                    }
                } else {
                    conjunctions.push({ type: 'literal', value: { [key]: value } });
                }
            }
        }
        
        return conjunctions;
    }
    
    // Compute cross product of OR branches
    function computeCrossProduct(conjunctions) {
        // Separate literals and OR terms
        const literals = [];
        const orTerms = [];
        
        for (const conj of conjunctions) {
            if (conj.type === 'literal') {
                literals.push(conj.value);
            } else {
                orTerms.push(conj.value);
            }
        }
        
        // If no OR terms, return combined literals
        if (orTerms.length === 0) {
            return [Object.assign({}, ...literals)];
        }
        
        // Start with the first OR term
        let branches = orTerms[0].map(branch => [branch]);
        
        // Cross product with remaining OR terms
        for (let i = 1; i < orTerms.length; i++) {
            const newBranches = [];
            for (const existingBranch of branches) {
                for (const newTerm of orTerms[i]) {
                    newBranches.push([...existingBranch, newTerm]);
                }
            }
            branches = newBranches;
        }
        
        // Combine each branch with literals
        return branches.map(branch => {
            const combined = Object.assign({}, ...literals, ...branch);
            // Recursively transform if needed
            if (needsTransformation(combined)) {
                const result = transformToDNF(combined);
                if (result.$or) {
                    // If recursive transformation returns multiple branches, 
                    // each should include the literals
                    return result.$or.map(subBranch => 
                        Object.assign({}, ...literals, subBranch)
                    );
                } else {
                    return Object.assign({}, ...literals, result);
                }
            }
            return combined;
        }).flat();
    }
    
    // Handle $nor transformation (De Morgan's law)
    function transformNor(query) {
        if (!query.$nor) return query;
        
        // $nor queries are complex and typically don't have perfect index coverage
        // For conservative analysis, we reject $nor queries as they require 
        // complex evaluation that goes beyond simple B-tree index usage
        
        // Create a special marker that will cause the query to be rejected
        return {
            ...query,
            __norPresent: true
        };
    }
    
    // Main transformation logic
    if (!needsTransformation(query)) {
        return query;
    }
    
    // Handle $nor first
    if (query.$nor) {
        query = transformNor(query);
    }
    
    const conjunctions = extractConjunctions(query);
    const dnfBranches = computeCrossProduct(conjunctions);
    
    // If single branch, return it directly
    if (dnfBranches.length === 1) {
        return dnfBranches[0];
    }
    
    return { $or: dnfBranches };
}

function parseSort(sort) {
    if (!sort || Object.keys(sort).length === 0) {
        return [];
    }
    
    return Object.entries(sort).map(([field, direction]) => ({
        field,
        direction: direction === 1 ? 'asc' : 'desc'
    }));
}

function hasIndexPerfectMatch(queryAnalysis, sortFields, indexes) {
    // Check for $nor presence - conservative rejection
    if (queryAnalysis.equalityFields && queryAnalysis.equalityFields.has('__norPresent')) {
        return false;
    }
    
    if (queryAnalysis.hasOr) {
        // For OR queries, each branch must have perfect match
        return queryAnalysis.orBranches.every(branch => 
            hasIndexPerfectMatch(branch, sortFields, indexes)
        );
    }
    
    // Check each index for perfect match
    for (const index of indexes) {
        
        if (indexPerfectlyMatches(queryAnalysis, sortFields, index)) {
            return true;
        }
    }
    
    return false;
}

function indexPerfectlyMatches(queryAnalysis, sortFields, index) {
    const indexFields = Object.entries(index.key).map(([field, direction]) => ({
        field,
        direction: direction === 1 ? 'asc' : 'desc'
    }));
    
    let indexPosition = 0;
    
    // Step 1: Match all equality fields (order doesn't matter within equality group)
    const equalityFields = Array.from(queryAnalysis.equalityFields);
    const unmatchedEquality = new Set(equalityFields);
    
    // For ESR to be valid, ALL equality fields must come first in the index
    // Find the longest prefix of equality fields
    let equalityEndPosition = 0;
    for (let i = 0; i < indexFields.length; i++) {
        const currentField = indexFields[i].field;
        if (equalityFields.includes(currentField)) {
            unmatchedEquality.delete(currentField);
            equalityEndPosition = i + 1;
        } else {
            // If we encounter a non-equality field, stop looking for equality fields
            // All remaining equality fields must come after this position, which violates ESR
            break;
        }
    }
    
    // If not all equality fields are covered in the prefix, this violates ESR
    if (unmatchedEquality.size > 0) {
        return false;
    }
    
    // Now set indexPosition to after all equality fields
    indexPosition = equalityEndPosition;
    
    // Step 2: Match sort fields (order and direction must match exactly or all reversed)
    if (sortFields.length > 0) {
        // Check if remaining index fields match sort specification
        const remainingIndexFields = indexFields.slice(indexPosition);
        
        if (remainingIndexFields.length < sortFields.length) {
            return false;
        }
        
        // Check for exact match first
        let exactMatch = true;
        let reverseMatch = true;
        
        // Check if any sort field is also a range field - this affects reverse traversal
        const rangeFields = Array.from(queryAnalysis.rangeFields);
        let hasRangeAndSortOnSameField = false;
        
        for (let i = 0; i < sortFields.length; i++) {
            const sortField = sortFields[i];
            const indexField = remainingIndexFields[i];
            
            // Check if this sort field is also used for range query
            if (rangeFields.includes(sortField.field)) {
                hasRangeAndSortOnSameField = true;
            }
            
            // Check exact match
            if (sortField.field !== indexField.field || 
                sortField.direction !== indexField.direction) {
                exactMatch = false;
            }
            
            // Check reverse match (all directions opposite)
            const reverseDirection = indexField.direction === 'asc' ? 'desc' : 'asc';
            if (sortField.field !== indexField.field || 
                sortField.direction !== reverseDirection) {
                reverseMatch = false;
            }
        }
        
        // If we have range and sort on the same field, reverse traversal is not allowed
        // because range queries require the index to be in the correct direction
        if (hasRangeAndSortOnSameField && !exactMatch) {
            return false;
        }
        
        // Must have either exact match or complete reverse match (if no range conflicts)
        if (!exactMatch && !reverseMatch) {
            return false;
        }
        
        indexPosition += sortFields.length;
    }
    
    // Step 3: Check range fields
    // Note: If a field was already used for sorting, it can also satisfy a range condition
    const rangeFields = Array.from(queryAnalysis.rangeFields);
    const coveredFields = new Set();
    
    // Add all fields from the index starting from the beginning up to current position
    for (let i = 0; i < indexPosition; i++) {
        coveredFields.add(indexFields[i].field);
    }
    
    // Add remaining index fields
    for (let i = indexPosition; i < indexFields.length; i++) {
        coveredFields.add(indexFields[i].field);
    }
    
    // Check if all range fields are covered somewhere in the index
    for (const rangeField of rangeFields) {
        if (!coveredFields.has(rangeField)) {
            return false;
        }
    }
    
    return true;
}

// Usage example:
// const query = { userId: 123, status: "active", createdAt: { $gte: new Date("2024-01-01") } };
// const sort = { createdAt: -1 };
// const namespace = "myapp.users";
// const result = analyzeIndexCoverage(query, sort, namespace);
// print("Perfect index match:", result);