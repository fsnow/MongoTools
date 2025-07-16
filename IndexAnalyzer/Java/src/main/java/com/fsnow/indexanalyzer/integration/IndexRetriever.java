package com.fsnow.indexanalyzer.integration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.fsnow.indexanalyzer.exception.IndexAnalyzerException;
import com.fsnow.indexanalyzer.model.IndexField;
import com.fsnow.indexanalyzer.model.MongoIndex;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retrieves index information from MongoDB collections.
 */
public class IndexRetriever {
    
    private static final Logger logger = LoggerFactory.getLogger(IndexRetriever.class);
    
    private final MongoClientAdapter mongoClientAdapter;
    
    public IndexRetriever(MongoClientAdapter mongoClientAdapter) {
        this.mongoClientAdapter = mongoClientAdapter;
    }
    
    /**
     * Retrieves all indexes for a given namespace.
     */
    public List<MongoIndex> getIndexes(String namespace) {
        MongoClientAdapter.NamespaceInfo namespaceInfo = mongoClientAdapter.parseNamespace(namespace);
        
        try {
            MongoCollection<Document> collection = mongoClientAdapter
                    .getDatabase(namespaceInfo.getDatabase())
                    .getCollection(namespaceInfo.getCollection());
            
            List<MongoIndex> indexes = new ArrayList<>();
            
            try (MongoCursor<Document> cursor = collection.listIndexes().iterator()) {
                while (cursor.hasNext()) {
                    Document indexDoc = cursor.next();
                    MongoIndex index = parseIndexDocument(indexDoc);
                    if (index != null) {
                        indexes.add(index);
                        logger.debug("Found index: {}", index);
                    }
                }
            }
            
            logger.info("Retrieved {} indexes for namespace {}", indexes.size(), namespace);
            return indexes;
            
        } catch (Exception e) {
            throw new IndexAnalyzerException(
                    String.format("Failed to retrieve indexes for namespace: %s", namespace), e);
        }
    }
    
    /**
     * Parses an index document from MongoDB.
     */
    private MongoIndex parseIndexDocument(Document indexDoc) {
        String name = indexDoc.getString("name");
        if (name == null) {
            logger.warn("Index without name found, skipping");
            return null;
        }
        
        Document key = indexDoc.get("key", Document.class);
        if (key == null || key.isEmpty()) {
            logger.warn("Index {} has no key definition, skipping", name);
            return null;
        }
        
        List<IndexField> fields = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : key.entrySet()) {
            String fieldName = entry.getKey();
            
            // Skip special index types like text indexes
            if ("_fts".equals(fieldName) || "_ftsx".equals(fieldName)) {
                logger.debug("Skipping text index: {}", name);
                return null;
            }
            
            int direction = ((Number) entry.getValue()).intValue();
            fields.add(new IndexField(fieldName, direction));
        }
        
        return new MongoIndex(name, fields);
    }
}