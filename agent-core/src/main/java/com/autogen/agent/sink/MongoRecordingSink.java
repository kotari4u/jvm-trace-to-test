package com.autogen.agent.sink;

import com.autogen.agent.api.RecordingSink;
import com.autogen.agent.config.AgentConfig;
import com.autogen.agent.model.RecordedTrace;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MongoRecordingSink implements RecordingSink {
    private final MongoClient client;
    private final MongoCollection<Document> collection;
    private final ReplaceOptions upsert = new ReplaceOptions().upsert(true);
    private final int ttlDays;
    private final AtomicBoolean ttlIndexEnsured = new AtomicBoolean(false);

    public MongoRecordingSink(AgentConfig config) {
        AgentConfig.MongoSinkConfig mongo = config.getStorage().getMongo();
        this.client = MongoClients.create(mongo.getConnectionString());
        this.collection = client.getDatabase(mongo.getDatabase()).getCollection(resolveCollectionName(config.getServiceName(), mongo.getCollection()));
        this.ttlDays = mongo.getTtlDays();
    }

    @Override
    public void write(RecordedTrace trace) {
        try {
            ensureTtlIndexOnce();
            Map<String, Object> payload = TraceYamlMapper.toYamlMap(trace);
            Document document = new Document(payload);
            document.put("_id", trace.getTraceId());
            document.put("recordedAt", Date.from(recordedAt(trace)));
            collection.replaceOne(Filters.eq("_id", trace.getTraceId()), document, upsert);
        } catch (Exception e) {
            System.err.println("[traffic-recorder-agent] failed to write trace to mongo: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private void ensureTtlIndexOnce() {
        if (ttlIndexEnsured.compareAndSet(false, true)) {
            ensureTtlIndex(ttlDays);
        }
    }

    private void ensureTtlIndex(int ttlDays) {
        try {
            collection.createIndex(
                    Indexes.ascending("recordedAt"),
                    new IndexOptions()
                            .name("recordedAt_ttl")
                            .expireAfter((long) ttlDays, TimeUnit.DAYS));
        } catch (Exception e) {
            System.err.println("[traffic-recorder-agent] failed to ensure mongo TTL index: " + e.getMessage());
        }
    }

    private String resolveCollectionName(String serviceName, String configuredCollection) {
        if (configuredCollection != null && !configuredCollection.trim().isEmpty()) {
            return sanitizeCollectionName(configuredCollection);
        }
        return sanitizeCollectionName(serviceName);
    }

    private String sanitizeCollectionName(String value) {
        String collectionName = value == null ? "unknown-service" : value.trim();
        if (collectionName.isEmpty()) {
            collectionName = "unknown-service";
        }
        collectionName = collectionName.replace('\0', '_').replace('$', '_');
        if (collectionName.startsWith("system.")) {
            collectionName = "svc_" + collectionName;
        }
        return collectionName;
    }

    private Instant recordedAt(RecordedTrace trace) {
        return trace.getRecordedAt() == null ? Instant.now() : trace.getRecordedAt();
    }
}
