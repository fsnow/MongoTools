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

// Insert sample documents
db.users.insertMany([
  {
    name: 'Alice Smith',
    age: 28,
    email: 'alice@example.com',
    city: 'New York',
    interests: ['reading', 'hiking', 'photography'],
    joined: new Date('2022-03-15'),
    isActive: true,
    score: 85.5
  },
  {
    name: 'Bob Johnson',
    age: 35,
    email: 'bob@example.com',
    city: 'Los Angeles',
    interests: ['coding', 'gaming', 'music'],
    joined: new Date('2021-11-20'),
    isActive: true,
    score: 92.0
  },
  {
    name: 'Charlie Brown',
    age: 42,
    email: 'charlie@example.com',
    city: 'Chicago',
    interests: ['sports', 'cooking'],
    joined: new Date('2023-01-05'),
    isActive: false,
    score: 78.3
  },
  {
    name: 'Diana Garcia',
    age: 31,
    email: 'diana@example.com',
    city: 'Miami',
    interests: ['dancing', 'traveling', 'painting'],
    joined: new Date('2022-07-10'),
    isActive: true,
    score: 88.7
  },
  {
    name: 'Ethan Lee',
    age: 25,
    email: 'ethan@example.com',
    city: 'San Francisco',
    interests: ['tech', 'movies', 'running'],
    joined: new Date('2023-05-01'),
    isActive: true,
    score: 90.2
  }
]);

// Query 1: Equality query
print("Query 1: Find user with name 'Alice Smith'");
db.users.find({ name: 'Alice Smith' });

// Query 2: Range query
print("Query 2: Find users between ages 30 and 40");
db.users.find({ age: { $gte: 30, $lte: 40 } });

// Query 3: Array contains
print("Query 3: Find users interested in 'hiking'");
db.users.find({ interests: 'hiking' });

// Query 4: Compound query
print("Query 4: Find active users in New York");
db.users.find({ isActive: true, city: 'New York' });

// Query 5: Projection (selected fields)
print("Query 5: Get names and emails of users with score > 85");
db.users.find({ score: { $gt: 85 } }, { name: 1, email: 1, _id: 0 });

// Query 6: Sort
print("Query 6: Get users sorted by age in descending order");
db.users.find().sort({ age: -1 });

// Query 7: Count
print("Query 7: Count active users");
db.users.countDocuments({ isActive: true });

// Query 8: Aggregation
print("Query 8: Average age of users by city");
db.users.aggregate([
  { $group: { _id: "$city", avgAge: { $avg: "$age" } } }
]);

// Query 9: Regex query
print("Query 9: Find users whose names start with 'A'");
db.users.find({ name: /^A/ });

// Query 10: Date range query
print("Query 10: Find users who joined between 2022-01-01 and 2022-12-31");
db.users.find({
  joined: {
    $gte: new Date('2022-01-01'),
    $lte: new Date('2022-12-31')
  }
});