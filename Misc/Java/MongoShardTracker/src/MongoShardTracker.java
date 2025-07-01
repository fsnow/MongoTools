import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.bson.Document;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MongoShardTracker {
    
    private static final Map<Integer, String> requestIdToCommandMap = new HashMap<>();
    private static final Map<Long, ServerAddress> cursorToServerMap = new ConcurrentHashMap<>();
    private static Long lastCursorId = null;
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -cp .:mongo-driver.jar MongoShardTracker <connection-string> <database-name> <collection-name>");
            System.exit(1);
        }
        
        String connectionString = args[0];
        String databaseName = args[1];
        String collectionName = args[2];
        
        System.out.println("Connecting to: " + connectionString);
        System.out.println("Database: " + databaseName);
        System.out.println("Collection: " + collectionName);
        
        // Configure command listener
        CommandListener shardTrackingListener = new CommandListener() {
            @Override
            public void commandStarted(CommandStartedEvent event) {
                if (event.getCommandName().equals("find") || 
                    event.getCommandName().equals("getMore")) {
                    requestIdToCommandMap.put(event.getRequestId(), event.getCommandName());
                    System.out.println("Command started: " + event.getCommandName() + 
                                       ", RequestId: " + event.getRequestId());
                    
                    // Print the actual command we're sending
                    if (event.getCommandName().equals("find")) {
                        System.out.println("Query: " + event.getCommand().toJson());
                    }
                }
            }

            @Override
            public void commandSucceeded(CommandSucceededEvent event) {
                String commandName = requestIdToCommandMap.get(event.getRequestId());
                if (commandName != null && 
                   (commandName.equals("find") || commandName.equals("getMore"))) {
                    
                    // Get information about the server that responded
                    ServerAddress serverAddress = event.getConnectionDescription().getServerAddress();
                    System.out.println("Response from server: " + 
                                      serverAddress.getHost() + ":" + 
                                      serverAddress.getPort());
                    
                    // Extract cursor ID if available and map it to the server
                    try {
                        String responseJson = event.getResponse().toJson();
                        Document response = Document.parse(responseJson);
                        
                        if (response.containsKey("cursor")) {
                            Document cursor = (Document) response.get("cursor");
                            if (cursor.containsKey("id")) {
                                Long cursorId = cursor.getLong("id");
                                
                                if (cursorId != null && cursorId != 0) {
                                    // Replace the existing mapping for this cursor ID
                                    cursorToServerMap.put(cursorId, serverAddress);
                                    System.out.println("Tracked cursor ID " + cursorId + 
                                                      " from server " + serverAddress.getHost() + 
                                                      ":" + serverAddress.getPort());
                                    
                                    // Update lastCursorId only for the most recent getMore/find
                                    lastCursorId = cursorId;
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Could not extract cursor information: " + e.getMessage());
                    }
                    
                    // Remove the request ID from our tracking map
                    requestIdToCommandMap.remove(event.getRequestId());
                }
            }

            @Override
            public void commandFailed(CommandFailedEvent event) {
                // Clean up tracking for failed commands
                String commandName = requestIdToCommandMap.get(event.getRequestId());
                if (commandName != null) {
                    System.out.println("Command failed: " + commandName + 
                                      ", RequestId: " + event.getRequestId());
                    requestIdToCommandMap.remove(event.getRequestId());
                }
            }
        };

        // Create MongoDB client with the command listener
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(connectionString))
            .addCommandListener(shardTrackingListener)
            .build();
        
        MongoClient mongoClient = MongoClients.create(settings);
        
        try {
            // Now you can perform your operations
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            System.out.println("Connected to MongoDB. Running find() operation with specific shardKey query...");
            
            // First, identify if this is a mongos router
            boolean isShardedCluster = false;
            Document serverStatus = null;
            try {
                serverStatus = database.runCommand(new Document("serverStatus", 1));
                if (serverStatus.containsKey("process") && 
                    serverStatus.getString("process").equals("mongos")) {
                    isShardedCluster = true;
                    System.out.println("Connected to mongos router - this is a sharded cluster");
                } else {
                    System.out.println("Not connected to mongos - this might not be a sharded cluster");
                }
            } catch (Exception e) {
                System.out.println("Could not determine if this is a sharded cluster: " + e.getMessage());
            }
            
            // Get shard information if available
            Map<String, String> shardHostMap = new HashMap<>();
            if (isShardedCluster) {
                try {
                    // First, get the connection mapping from mongos to mongod instances
                    Document netstatOutput = mongoClient.getDatabase("admin")
                        .runCommand(new Document("netstat", 1));
                    
                    // Map to store mongos to mongod connections
                    Map<Integer, String> mongosToShardMap = new HashMap<>();
                    
                    // Process netstat output to identify mongos to mongod connections
                    if (netstatOutput.containsKey("connections")) {
                        for (Document conn : netstatOutput.getList("connections", Document.class)) {
                            // We're looking for outbound connections from mongos to mongod
                            if (conn.containsKey("remote") && conn.containsKey("localPort")) {
                                String remote = conn.getString("remote");
                                Integer localPort = conn.getInteger("localPort");
                                
                                // Store the connection mapping
                                mongosToShardMap.put(localPort, remote);
                            }
                        }
                    }
                    
                    // Get shard information to map hosts to shard IDs
                    Document adminDb = mongoClient.getDatabase("admin")
                        .runCommand(new Document("listShards", 1));
                    
                    if (adminDb.containsKey("shards")) {
                        System.out.println("Shard information:");
                        Map<String, String> hostToShardMap = new HashMap<>();
                        
                        for (Document shard : adminDb.getList("shards", Document.class)) {
                            String shardId = shard.getString("_id");
                            String host = shard.getString("host");
                            System.out.println("  Shard: " + shardId + " - Host: " + host);
                            
                            // Extract host information for matching
                            String[] hostParts = host.split("/");
                            if (hostParts.length > 1) {
                                String[] servers = hostParts[1].split(",");
                                for (String server : servers) {
                                    hostToShardMap.put(server, shardId);
                                    
                                    // Also map individual host:port combinations
                                    String[] serverParts = server.split(":");
                                    if (serverParts.length > 1) {
                                        // Map both by hostname and by hostname:port
                                        hostToShardMap.put(serverParts[0], shardId);
                                        hostToShardMap.put(server, shardId);
                                    }
                                }
                            }
                        }
                        
                        // Now, map mongos ports to shard IDs for our tracking
                        for (Map.Entry<Integer, String> entry : mongosToShardMap.entrySet()) {
                            Integer mongosPort = entry.getKey();
                            String mongodHost = entry.getValue();
                            
                            if (hostToShardMap.containsKey(mongodHost)) {
                                String shardId = hostToShardMap.get(mongodHost);
                                shardHostMap.put("localhost:" + mongosPort, shardId);
                                shardHostMap.put(String.valueOf(mongosPort), shardId);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Could not get shard information: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // Alternative approach - make separate queries for each shardKey value
            int[] shardKeyValues = {50, 150, 250};
            Map<String, Integer> shardDocumentCounts = new HashMap<>();

            for (int shardKeyValue : shardKeyValues) {
                Document query = new Document("shardKey", shardKeyValue);
                
                MongoCursor<Document> cursor = collection.find(query)
                    .projection(Projections.include("_id", "shardKey"))
                    .batchSize(1)
                    .iterator();
                
                if (cursor.hasNext()) {
                    Document doc = cursor.next();
                    Object id = doc.get("_id");
                    
                    // After the first next() call, the lastCursorId should be updated
                    ServerAddress sourceServer = null;
                    if (lastCursorId != null) {
                        sourceServer = cursorToServerMap.get(lastCursorId);
                    }
                    
                    if (sourceServer != null) {
                        String shardId = "unknown";
                        
                        // Try matching server to shard name with different formats
                        String hostWithPort = sourceServer.getHost() + ":" + sourceServer.getPort();
                        String portOnly = String.valueOf(sourceServer.getPort());
                        
                        if (shardHostMap.containsKey(hostWithPort)) {
                            shardId = shardHostMap.get(hostWithPort);
                        } else if (shardHostMap.containsKey(portOnly)) {
                            shardId = shardHostMap.get(portOnly);
                        } else if (shardHostMap.containsKey(sourceServer.getHost())) {
                            shardId = shardHostMap.get(sourceServer.getHost());
                        }
                        
                        System.out.println("Document with ID: " + id + 
                                          ", shardKey: " + shardKeyValue +
                                          " from mongos: " + sourceServer.getHost() + ":" + 
                                          sourceServer.getPort() + " (Shard: " + shardId + ")");
                        
                        // Track document counts by shard
                        shardDocumentCounts.put(shardId, 
                                              shardDocumentCounts.getOrDefault(shardId, 0) + 1);
                    } else {
                        System.out.println("Document with ID: " + id + 
                                          ", shardKey: " + shardKeyValue +
                                          " (unable to determine source shard)");
                    }
                }
                
                cursor.close();
                
                // Add a small delay to ensure events are processed
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            System.out.println("Find operations completed.");
            
            // Print document distribution across shards
            System.out.println("\nDocument distribution across shards:");
            for (Map.Entry<String, Integer> entry : shardDocumentCounts.entrySet()) {
                System.out.println("  Shard " + entry.getKey() + ": " + entry.getValue() + " documents");
            }
            
        } catch (Exception e) {
            System.err.println("Error during MongoDB operation: " + e.getMessage());
            e.printStackTrace();
        } finally {
            mongoClient.close();
            System.out.println("MongoDB connection closed.");
        }
    }
}