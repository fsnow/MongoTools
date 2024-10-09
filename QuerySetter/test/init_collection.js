// enable $queryStats
db.adminCommand(
  {
    setParameter: 1,
    internalQueryStatsRateLimit: 100
  }
);

// Connect to the database (or create if it doesn't exist)
db = db.getSiblingDB("test");

// Drop the collection if it exists
db.users.drop();

// Function to execute a query safely
function safeQuery(queryName, queryFunc) {
  print(queryName);
  try {
    const result = queryFunc();
    if (result && typeof result.toArray === 'function') {
      result.toArray();  // Execute the query
    }
    print("Query executed successfully");
  } catch (error) {
    print(`Error in ${queryName}: ${error.message}`);
  }
  sleep(200);
}

// Insert sample documents with various BSON types
db.users.insertMany([
  {
    name: 'Alice Smith',
    age: 28,
    email: 'alice@example.com',
    city: 'New York',
    interests: ['reading', 'hiking', 'photography'],
    joined: new Date('2022-03-15'),
    isActive: true,
    score: 85.5,
    profile: { bio: 'Adventurous spirit' },
    loginTime: new Date(),
    lastLogin: ISODate("2023-09-15T14:30:00Z"),
    preferences: {
      theme: 'dark',
      notifications: true
    },
    data: BinData(0, "SGVsbG8gV29ybGQ="),
    uniqueId: new ObjectId(),
    paymentInfo: {
      type: 'credit',
      cardNumber: NumberLong("1234567890123456")
    }
  },
  {
    name: 'Bob Johnson',
    age: 35,
    email: 'bob@example.com',
    city: 'Los Angeles',
    interests: ['coding', 'gaming', 'music'],
    joined: new Date('2021-11-20'),
    isActive: true,
    score: 92.0,
    profile: { bio: 'Tech enthusiast' },
    loginTime: new Timestamp(),
    lastLogin: ISODate("2023-10-01T09:45:00Z"),
    preferences: {
      theme: 'light',
      notifications: false
    },
    data: BinData(0, "QmluYXJ5IERhdGE="),
    uniqueId: new ObjectId(),
    paymentInfo: {
      type: 'paypal',
      accountId: UUID()
    }
  }
]);

// Queries wrapped in safeQuery function
safeQuery("Query 1: Find user with name 'Alice Smith'", () => 
  db.users.find({ name: 'Alice Smith' }).toArray()
);

safeQuery("Query 2: Find users between ages 25 and 40", () => 
  db.users.find({ age: { $gte: 25, $lte: 40 } }).toArray()
);

safeQuery("Query 3: Find users interested in 'coding'", () => 
  db.users.find({ interests: 'coding' }).toArray()
);

safeQuery("Query 4: Find active users in Los Angeles", () => 
  db.users.find({ isActive: true, city: 'Los Angeles' }).toArray()
);

safeQuery("Query 5: Get names and emails of users with score > 85", () => 
  db.users.find({ score: { $gt: 85 } }, { name: 1, email: 1, _id: 0 }).toArray()
);

safeQuery("Query 6: Get users sorted by age in descending order", () => 
  db.users.find().sort({ age: -1 }).toArray()
);

safeQuery("Query 7: Count active users", () => 
  db.users.countDocuments({ isActive: true })
);

safeQuery("Query 8: Average age of users by city", () => 
  db.users.aggregate([
    { $group: { _id: "$city", avgAge: { $avg: "$age" } } }
  ]).toArray()
);

safeQuery("Query 9: Find users whose names start with 'A'", () => 
  db.users.find({ name: /^A/ }).toArray()
);

safeQuery("Query 10: Find users who joined between 2021-01-01 and 2022-12-31", () => 
  db.users.find({
    joined: {
      $gte: ISODate("2021-01-01T00:00:00Z"),
      $lte: ISODate("2022-12-31T23:59:59Z")
    }
  }).toArray()
);

safeQuery("Query 11: Find users with 'dark' theme preference", () => 
  db.users.find({ "preferences.theme": "dark" }).toArray()
);

safeQuery("Query 12: Find users with a specific ObjectId", () => 
  db.users.find({ uniqueId: ObjectId("000000000000000000000000") }).toArray()
);

safeQuery("Query 13: Find users with login time after a specific timestamp", () => 
  db.users.find({ loginTime: { $gt: new Timestamp(1600000000, 1) } }).toArray()
);

safeQuery("Query 14: Find users with credit card payment type", () => 
  db.users.find({ "paymentInfo.type": "credit" }).toArray()
);

// Add two new documents with MinKey and MaxKey
db.users.insertMany([
  {
    name: 'Min User',
    score: MinKey(),
    rank: MinKey()
  },
  {
    name: 'Max User',
    score: MaxKey(),
    rank: MaxKey()
  }
]);

safeQuery("Query 15: Find users with rank between 100 and MaxKey", () => 
  db.users.find({ rank: { $gt: NumberInt("100"), $lt: MaxKey() } }).toArray()
);

safeQuery("Query 16: Find users with score greater than MinKey", () => 
  db.users.find({ score: { $gt: MinKey() } }).toArray()
);

safeQuery("Query 17: Find users with rank less than MaxKey", () => 
  db.users.find({ rank: { $lt: MaxKey() } }).toArray()
);

safeQuery("Query 18: Sort users by score in ascending order (MinKey will be first)", () => 
  db.users.find().sort({ score: 1 }).toArray()
);

safeQuery("Query 19: Sort users by rank in descending order (MaxKey will be first)", () => 
  db.users.find().sort({ rank: -1 }).toArray()
);

safeQuery("Query 20: Find users with score between MinKey and 50", () => 
  db.users.find({ score: { $gt: MinKey(), $lt: NumberInt("50") } }).toArray()
);

safeQuery("Query 21: Find users with rank between 100 and MaxKey", () => 
  db.users.find({ rank: { $gt: NumberInt("100"), $lt: MaxKey() } }).toArray()
);

// Insert new documents with various array types and objects
db.users.insertMany([
  {
    name: 'Array User',
    emptyArray: [],
    mixedArray: [1, "two", true, null, new Date(), { key: "value" }],
    objectField: { nested: { field: "value" } },
    bsonTypeArray: [
      NumberInt(42),
      NumberLong(1234567890),
      NumberDecimal("123.456"),
      "String",
      true,
      new Date(),
      null,
      new ObjectId(),
      BinData(0, "QmluYXJ5"),
      [1, 2, 3],
      { key: "value" },
      new Timestamp(1000, 1),
      MinKey(),
      MaxKey(),
      /pattern/,
      new DBRef("collection", ObjectId()),
      UUID()
    ]
  }
]);

safeQuery("Query 22: Search for an object", () => 
  db.users.find({ objectField: { nested: { field: "value" } } }).toArray()
);

safeQuery("Query 23: Search for users with an empty array", () => 
  db.users.find({ emptyArray: [] }).toArray()
);

safeQuery("Query 24: Search for users with a mixed type array containing a specific value", () => 
  db.users.find({ mixedArray: "two" }).toArray()
);

safeQuery("Query 25: Search for users with a specific NumberInt in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: NumberInt(42) }).toArray()
);

safeQuery("Query 26: Search for users with a specific NumberLong in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: NumberLong("1234567890") }).toArray()
);

safeQuery("Query 27: Search for users with a specific NumberDecimal in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: NumberDecimal("123.456") }).toArray()
);

safeQuery("Query 28: Search for users with a specific String in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: "String" }).toArray()
);

safeQuery("Query 29: Search for users with a Boolean true in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: true }).toArray()
);

safeQuery("Query 30: Search for users with a Date in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: { $type: "date" } }).toArray()
);

safeQuery("Query 31: Search for users with a null in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: null }).toArray()
);

safeQuery("Query 32: Search for users with an ObjectId in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: { $type: "objectId" } }).toArray()
);

safeQuery("Query 33: Search for users with BinData in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: { $type: "binData" } }).toArray()
);

safeQuery("Query 34: Search for users with an array in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: { $elemMatch: { $type: "array" } } }).toArray()
);

safeQuery("Query 35: Search for users with an object in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: { $elemMatch: { $type: "object" } } }).toArray()
);

safeQuery("Query 36: Search for users with a Timestamp in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: { $type: "timestamp" } }).toArray()
);

safeQuery("Query 37: Search for users with MinKey in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: MinKey() }).toArray()
);

safeQuery("Query 38: Search for users with MaxKey in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: MaxKey() }).toArray()
);

safeQuery("Query 39: Search for users with a RegExp in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: { $type: "regex" } }).toArray()
);

safeQuery("Query 40: Search for users with a DBRef in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: { $type: "dbPointer" } }).toArray()
);

safeQuery("Query 41: Search for users with a UUID in bsonTypeArray", () => 
  db.users.find({ bsonTypeArray: { $type: "binData" } }).toArray()
);

safeQuery("Query 42: Count the number of elements in the bsonTypeArray", () => 
  db.users.aggregate([
    {
      $project: {
        arraySize: {
          $cond: {
            if: { $isArray: "$bsonTypeArray" },
            then: { $size: "$bsonTypeArray" },
            else: "Not an array or field does not exist"
          }
        }
      }
    }
  ])
);

// Insert new documents with specific array literals
db.users.insertMany([
  {
    name: 'Literal Array User',
    emptyArray: [],
    mixedArray: [1, "a"],
    numberIntArray: [NumberInt(1), NumberInt(2)],
    numberLongArray: [NumberLong(1234567890), NumberLong(9876543210)],
    numberDecimalArray: [NumberDecimal("1.23"), NumberDecimal("4.56")],
    stringArray: ["hello", "world"],
    booleanArray: [true, false],
    dateArray: [new Date("2023-01-01"), new Date("2023-12-31")],
    nullArray: [null, null],
    objectIdArray: [new ObjectId(), new ObjectId()],
    binaryArray: [BinData(0, "aGVsbG8="), BinData(0, "d29ybGQ=")],
    nestedArray: [[1, 2], [3, 4]],
    objectArray: [{ key1: "value1" }, { key2: "value2" }],
    timestampArray: [new Timestamp(1000, 1), new Timestamp(2000, 1)],
    minKeyArray: [MinKey(), MinKey()],
    maxKeyArray: [MaxKey(), MaxKey()],
    regexArray: [/pattern1/, /pattern2/],
    dbRefArray: [new DBRef("collection1", ObjectId()), new DBRef("collection2", ObjectId())],
    uuidArray: [UUID(), UUID()]
  }
]);

safeQuery("Query 43: Search for users with a literal empty array", () => 
  db.users.find({ emptyArray: [] }).toArray()
);

safeQuery("Query 44: Search for users with a literal mixed type array", () => 
  db.users.find({ mixedArray: [1, "a"] }).toArray()
);

safeQuery("Query 45: Search for users with a literal NumberInt array", () => 
  db.users.find({ numberIntArray: [NumberInt(1), NumberInt(2)] }).toArray()
);

safeQuery("Query 46: Search for users with a literal NumberLong array", () => 
  db.users.find({ numberLongArray: [NumberLong("1234567890"), NumberLong("9876543210")] }).toArray()
);

safeQuery("Query 47: Search for users with a literal NumberDecimal array", () => 
  db.users.find({ numberDecimalArray: [NumberDecimal("1.23"), NumberDecimal("4.56")] }).toArray()
);

safeQuery("Query 48: Search for users with a literal String array", () => 
  db.users.find({ stringArray: ["hello", "world"] }).toArray()
);

safeQuery("Query 49: Search for users with a literal Boolean array", () => 
  db.users.find({ booleanArray: [true, false] }).toArray()
);

safeQuery("Query 50: Search for users with a literal Date array", () => 
  db.users.find({ dateArray: [new Date("2023-01-01"), new Date("2023-12-31")] }).toArray()
);

safeQuery("Query 51: Search for users with a literal null array", () => 
  db.users.find({ nullArray: [null, null] }).toArray()
);

safeQuery("Query 52: Search for users with a literal ObjectId array", () => 
  db.users.find({
    objectIdArray: {
      $size: 2,
      $elemMatch: { $type: "objectId" }
    }
  }).toArray()
);

safeQuery("Query 53: Search for users with a literal Binary array", () => 
  db.users.find({
    binaryArray: {
      $size: 2,
      $elemMatch: { $type: "binData" }
    }
  }).toArray()
);

safeQuery("Query 54: Search for users with a literal nested array", () => 
  db.users.find({ nestedArray: [[1, 2], [3, 4]] }).toArray()
);

safeQuery("Query 55: Search for users with a literal object array", () => 
  db.users.find({ objectArray: [{ key1: "value1" }, { key2: "value2" }] }).toArray()
);

safeQuery("Query 56: Search for users with a literal Timestamp array", () => 
  db.users.find({
    timestampArray: {
      $size: 2,
      $elemMatch: { $type: "timestamp" }
    }
  }).toArray()
);

safeQuery("Query 57: Search for users with a literal MinKey array", () => 
  db.users.find({ minKeyArray: [MinKey(), MinKey()] }).toArray()
);

safeQuery("Query 58: Search for users with a literal MaxKey array", () => 
  db.users.find({ maxKeyArray: [MaxKey(), MaxKey()] }).toArray()
);

safeQuery("Query 59: Search for users with a literal RegExp array", () => 
  db.users.find({
    regexArray: {
      $size: 2,
      $elemMatch: { $type: "regex" }
    }
  }).toArray()
);

safeQuery("Query 60: Search for users with a literal DBRef array", () => 
  db.users.find({
    dbRefArray: {
      $size: 2,
      $elemMatch: { $type: "dbPointer" }
    }
  }).toArray()
);

safeQuery("Query 61: Search for users with a literal UUID array", () => 
  db.users.find({
    uuidArray: {
      $size: 2,
      $elemMatch: { $type: "binData" }
    }
  }).toArray()
);
