package com.fsnow.indexanalyzer.integration;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.fsnow.indexanalyzer.exception.IndexAnalyzerException;
import com.fsnow.indexanalyzer.exception.InvalidNamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

/**
 * Adapter for MongoDB client operations.
 */
public class MongoClientAdapter implements Closeable {
    
    private static final Logger logger = LoggerFactory.getLogger(MongoClientAdapter.class);
    
    private final MongoClient mongoClient;
    private final int timeoutMs;
    
    public MongoClientAdapter(String connectionString, int timeoutMs) {
        this.timeoutMs = timeoutMs;
        
        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .applyToSocketSettings(builder -> 
                            builder.connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                                   .readTimeout(timeoutMs, TimeUnit.MILLISECONDS))
                    .build();
            
            this.mongoClient = MongoClients.create(settings);
            logger.info("Successfully connected to MongoDB");
        } catch (Exception e) {
            throw new IndexAnalyzerException("Failed to connect to MongoDB", e);
        }
    }
    
    /**
     * Gets a database by name.
     */
    public MongoDatabase getDatabase(String databaseName) {
        return mongoClient.getDatabase(databaseName);
    }
    
    /**
     * Parses a namespace and returns the database.
     */
    public NamespaceInfo parseNamespace(String namespace) {
        if (namespace == null || !namespace.contains(".")) {
            throw new InvalidNamespaceException(namespace);
        }
        
        String[] parts = namespace.split("\\.", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new InvalidNamespaceException(namespace);
        }
        
        return new NamespaceInfo(parts[0], parts[1]);
    }
    
    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("MongoDB connection closed");
        }
    }
    
    /**
     * Holds parsed namespace information.
     */
    public static class NamespaceInfo {
        private final String database;
        private final String collection;
        
        public NamespaceInfo(String database, String collection) {
            this.database = database;
            this.collection = collection;
        }
        
        public String getDatabase() {
            return database;
        }
        
        public String getCollection() {
            return collection;
        }
    }
}