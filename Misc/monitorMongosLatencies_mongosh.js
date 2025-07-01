// MongoDB Mongos Latency Monitor for mongosh
// Specifically designed to monitor latencies to mongos routers

// Configuration
const monitoringInterval = 5000; // Check every 5 seconds
const verbose = true; // Set to false for less output

// Store mongos addresses and start times for pings
const mongosNodes = [];
const pingStartTimes = new Map();

// Function to measure RTT to a specific mongos
function measureLatencyToMongos(mongosAddress) {
  const start = Date.now();
  pingStartTimes.set(mongosAddress, start);
  
  try {
    // Use admin command to ping the server with hostAddress option to target specific mongos
    const result = db.adminCommand({ ping: 1 }, { hostAddress: mongosAddress });
    const end = Date.now();
    const rtt = end - pingStartTimes.get(mongosAddress);
    
    print(`RTT to mongos ${mongosAddress}: ${rtt} ms`);
    return rtt;
  } catch (err) {
    print(`Error pinging mongos ${mongosAddress}: ${err.message}`);
    return null;
  }
}

// Discover all mongos instances in the topology
function discoverMongosNodes() {
  try {
    // First, verify we're connected to a sharded cluster
    const isMaster = db.adminCommand({ ismaster: 1 });
    
    if (isMaster.msg !== "isdbgrid") {
      print("Warning: Not connected to a mongos router. This script is designed to monitor mongos routers.");
      return false;
    }
    
    print("Connected to a mongos router in a sharded cluster");
    
    // Get information about the current connection
    const connStatus = db.adminCommand({ connPoolStats: 1 });
    
    if (verbose) {
      print("Connection pool status:");
      printjson(connStatus);
    }
    
    // Get the mongos we're currently connected to
    mongosNodes.push(isMaster.me);
    
    // Try to detect other mongos instances through sharding configuration
    const configDB = db.getSiblingDB("config");
    const mongosCollection = configDB.mongos;
    
    if (mongosCollection) {
      const otherMongos = mongosCollection.find({}, { _id: 1 }).toArray();
      if (verbose) {
        print("Found mongos instances in config.mongos:");
        printjson(otherMongos);
      }
      
      otherMongos.forEach(doc => {
        if (!mongosNodes.includes(doc._id)) {
          mongosNodes.push(doc._id);
        }
      });
    }
    
    print(`Found ${mongosNodes.length} mongos node(s) to monitor: ${mongosNodes.join(", ")}`);
    return true;
  } catch (err) {
    print(`Error discovering mongos nodes: ${err.message}`);
    return false;
  }
}

// Main monitoring function
function monitorMongosLatencies() {
  print("Starting MongoDB mongos latency monitor in mongosh...");
  
  const success = discoverMongosNodes();
  if (!success || mongosNodes.length === 0) {
    print("No mongos nodes found to monitor. Aborting.");
    return null;
  }
  
  // Run the first measurement immediately
  mongosNodes.forEach(mongos => {
    measureLatencyToMongos(mongos);
  });
  
  // Set up ongoing monitoring at regular intervals
  print(`Starting continuous monitoring every ${monitoringInterval/1000} seconds (press Ctrl+C to stop)...`);
  
  // Use setInterval for continuous monitoring
  const intervalId = setInterval(() => {
    mongosNodes.forEach(mongos => {
      measureLatencyToMongos(mongos);
    });
  }, monitoringInterval);
  
  // Return the interval ID so it can be cleared if needed
  return intervalId;
}

// Start monitoring
const monitor = monitorMongosLatencies();

// Instructions to stop
if (monitor) {
  print("\nTo stop monitoring, run: clearInterval(monitor)");
} else {
  print("Monitoring did not start successfully.");
}