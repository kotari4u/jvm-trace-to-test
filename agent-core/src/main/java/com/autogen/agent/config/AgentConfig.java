package com.autogen.agent.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentConfig {
    private String serviceName = "unknown-service";
    private boolean enabled = true;
    private StorageConfig storage = new StorageConfig();
    private CaptureConfig kafka = new CaptureConfig(true);
    private CaptureConfig http = new CaptureConfig(true);
    private CaptureConfig redis = new CaptureConfig(true);
    private CaptureConfig mongo = new CaptureConfig(true);
    private PayloadConfig payload = new PayloadConfig();
    private RedactionConfig redaction = new RedactionConfig();
    private AsyncConfig async = new AsyncConfig();

    public static AgentConfig load(String path) {
        AgentConfig config = new AgentConfig();
        String configuredPath = firstNonBlank(path, System.getProperty("jvm.trace-to-test.config"), System.getenv("JVM_TRACE_TO_TEST_CONFIG"));
        if (configuredPath != null) {
            loadFromPath(config, Path.of(configuredPath), true);
            return config;
        }

        for (Path candidate : defaultApplicationConfigPaths()) {
            if (Files.exists(candidate)) {
                loadFromPath(config, candidate, false);
                return config;
            }
        }
        return config;
    }

    private static void loadFromPath(AgentConfig config, Path configPath, boolean explicitAgentConfig) {
        if (!Files.exists(configPath)) {
            return;
        }
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            Iterable<Object> documents = new Yaml().loadAll(inputStream);
            for (Object loaded : documents) {
                if (loaded instanceof Map) {
                    Map<String, Object> yaml = asMap(loaded);
                    String springServiceName = springApplicationName(yaml);
                    Map<String, Object> agentConfig = agentConfigSection(yaml, explicitAgentConfig);
                    if (!agentConfig.isEmpty()) {
                        config.apply(agentConfig);
                    }
                    if (isBlank(config.serviceName) && springServiceName != null) {
                        config.serviceName = springServiceName;
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load agent config: " + configPath, e);
        }
    }

    private static List<Path> defaultApplicationConfigPaths() {
        return Arrays.asList(
                Path.of("./application.yml"),
                Path.of("./application.yaml"),
                Path.of("./config/application.yml"),
                Path.of("./config/application.yaml"),
                Path.of("./src/main/resources/application.yml"),
                Path.of("./src/main/resources/application.yaml"));
    }

    private static Map<String, Object> agentConfigSection(Map<String, Object> yaml, boolean explicitAgentConfig) {
        for (String key : Arrays.asList("jvmTraceToTest", "jvm-trace-to-test", "traceToTest", "trafficRecorderAgent", "trafficRecorder")) {
            Map<String, Object> section = map(yaml.get(key));
            if (!section.isEmpty()) {
                return section;
            }
        }
        return explicitAgentConfig ? yaml : Collections.emptyMap();
    }

    private static String springApplicationName(Map<String, Object> yaml) {
        Map<String, Object> spring = map(yaml.get("spring"));
        Map<String, Object> application = map(spring.get("application"));
        return string(application.get("name"), null);
    }

    private void apply(Map<String, Object> yaml) {
        Map<String, Object> agent = map(yaml.get("agent"));
        serviceName = string(firstPresent(agent.get("serviceName"), yaml.get("serviceName")), serviceName);
        enabled = bool(firstPresent(agent.get("enabled"), yaml.get("enabled")), enabled);
        storage.apply(map(yaml.get("storage")));
        kafka.apply(map(yaml.get("kafka")));
        http.apply(map(yaml.get("http")));
        redis.apply(map(yaml.get("redis")));
        mongo.apply(map(yaml.get("mongo")));
        payload.apply(map(yaml.get("payload")));
        redaction.apply(map(yaml.get("redaction")));
        async.apply(map(yaml.get("async")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "unknown-service".equals(value);
    }

    private static boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return new ArrayList<>();
    }

    public String getServiceName() {
        return serviceName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public StorageConfig getStorage() {
        return storage;
    }

    public CaptureConfig getKafka() {
        return kafka;
    }

    public CaptureConfig getHttp() {
        return http;
    }

    public CaptureConfig getRedis() {
        return redis;
    }

    public CaptureConfig getMongo() {
        return mongo;
    }

    public PayloadConfig getPayload() {
        return payload;
    }

    public RedactionConfig getRedaction() {
        return redaction;
    }

    public AsyncConfig getAsync() {
        return async;
    }

    public static class StorageConfig {
        private String type = "yaml";
        private String outputDir = "./recordings";
        private String fileStrategy = "trace-per-file";
        private KafkaSinkConfig kafka = new KafkaSinkConfig();
        private MongoSinkConfig mongo = new MongoSinkConfig();

        private void apply(Map<String, Object> map) {
            type = string(map.get("type"), type);
            outputDir = string(map.get("outputDir"), outputDir);
            fileStrategy = string(map.get("fileStrategy"), fileStrategy);
            kafka.apply(map(map.get("kafka")));
            mongo.apply(map(map.get("mongo")));
        }

        public String getType() {
            return type;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public String getFileStrategy() {
            return fileStrategy;
        }

        public KafkaSinkConfig getKafka() {
            return kafka;
        }

        public MongoSinkConfig getMongo() {
            return mongo;
        }
    }

    public static class KafkaSinkConfig {
        private String bootstrapServers = "localhost:9092";
        private String topic = "traffic-recordings";
        private String clientId = "traffic-recorder-agent";
        private String compressionType = "lz4";
        private String acks = "1";

        private void apply(Map<String, Object> map) {
            bootstrapServers = string(map.get("bootstrapServers"), bootstrapServers);
            topic = string(map.get("topic"), topic);
            clientId = string(map.get("clientId"), clientId);
            compressionType = string(map.get("compressionType"), compressionType);
            acks = string(map.get("acks"), acks);
        }

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public String getTopic() {
            return topic;
        }

        public String getClientId() {
            return clientId;
        }

        public String getCompressionType() {
            return compressionType;
        }

        public String getAcks() {
            return acks;
        }
    }

    public static class MongoSinkConfig {
        private String connectionString = "mongodb://localhost:27017";
        private String database = "traffic_recorder";
        private String collection;
        private int ttlDays = 30;

        private void apply(Map<String, Object> map) {
            connectionString = string(map.get("connectionString"), connectionString);
            database = string(map.get("database"), database);
            if (map.containsKey("collection")) {
                collection = string(map.get("collection"), collection);
            }
            ttlDays = integer(map.get("ttlDays"), ttlDays);
        }

        public String getConnectionString() {
            return connectionString;
        }

        public String getDatabase() {
            return database;
        }

        public String getCollection() {
            return collection;
        }

        public int getTtlDays() {
            return ttlDays;
        }
    }

    public static class CaptureConfig {
        private boolean enabled;
        private boolean capturePayloads = true;
        private List<String> include = new ArrayList<>();
        private List<String> exclude = new ArrayList<>();

        CaptureConfig(boolean enabled) {
            this.enabled = enabled;
        }

        private void apply(Map<String, Object> map) {
            enabled = bool(map.get("enabled"), enabled);
            capturePayloads = bool(map.get("capturePayloads"), capturePayloads);
            include = strings(map.getOrDefault("include", map.get("includeTopics")));
            exclude = strings(map.getOrDefault("exclude", map.get("excludeTopics")));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isCapturePayloads() {
            return capturePayloads;
        }

        public List<String> getInclude() {
            return include;
        }

        public List<String> getExclude() {
            return exclude;
        }

        public boolean allows(String value) {
            String candidate = value == null ? "" : value;
            if (!include.isEmpty() && include.stream().noneMatch(pattern -> matches(pattern, candidate))) {
                return false;
            }
            return exclude.stream().noneMatch(pattern -> matches(pattern, candidate));
        }

        private boolean matches(String pattern, String value) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return value.matches(regex);
        }
    }

    public static class PayloadConfig {
        private int maxBytes = 65536;
        private boolean prettyPrintJson = true;
        private String binaryPolicy = "base64";

        private void apply(Map<String, Object> map) {
            maxBytes = integer(map.get("maxBytes"), maxBytes);
            prettyPrintJson = bool(map.get("prettyPrintJson"), prettyPrintJson);
            binaryPolicy = string(map.get("binaryPolicy"), binaryPolicy);
        }

        public int getMaxBytes() {
            return maxBytes;
        }

        public boolean isPrettyPrintJson() {
            return prettyPrintJson;
        }

        public String getBinaryPolicy() {
            return binaryPolicy;
        }
    }

    public static class RedactionConfig {
        private List<String> headers = new ArrayList<>();
        private List<String> jsonFields = new ArrayList<>();
        private List<String> mongoFields = new ArrayList<>();
        private List<String> redisKeyPatterns = new ArrayList<>();

        private void apply(Map<String, Object> map) {
            headers = strings(map.get("headers"));
            jsonFields = strings(map.getOrDefault("jsonFields", map.get("jsonPaths")));
            mongoFields = strings(map.get("mongoFields"));
            redisKeyPatterns = strings(map.get("redisKeyPatterns"));
        }

        public Map<String, Object> asMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("headers", headers);
            value.put("jsonFields", jsonFields);
            value.put("mongoFields", mongoFields);
            value.put("redisKeyPatterns", redisKeyPatterns);
            return value;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<String> getJsonFields() {
            return jsonFields;
        }

        public List<String> getMongoFields() {
            return mongoFields;
        }

        public List<String> getRedisKeyPatterns() {
            return redisKeyPatterns;
        }
    }

    public static class AsyncConfig {
        private boolean enabled = true;
        private int workerThreads = 1;
        private int queueSize = 10000;
        private long shutdownDrainMillis = 5000;

        private void apply(Map<String, Object> map) {
            enabled = bool(map.get("enabled"), enabled);
            workerThreads = integer(map.get("workerThreads"), workerThreads);
            queueSize = integer(map.get("queueSize"), queueSize);
            shutdownDrainMillis = integer(map.get("shutdownDrainMillis"), (int) shutdownDrainMillis);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public long getShutdownDrainMillis() {
            return shutdownDrainMillis;
        }
    }
}
