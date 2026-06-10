package com.autogen.agent.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static AgentConfig load(String path) {
        AgentConfig config = new AgentConfig();
        if (path == null || path.trim().isEmpty()) {
            return config;
        }
        Path configPath = Path.of(path);
        if (!Files.exists(configPath)) {
            return config;
        }
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            Object loaded = new Yaml().load(inputStream);
            if (loaded instanceof Map) {
                config.apply(asMap(loaded));
            }
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load agent config: " + path, e);
        }
    }

    private void apply(Map<String, Object> yaml) {
        Map<String, Object> agent = map(yaml.get("agent"));
        serviceName = string(agent.get("serviceName"), serviceName);
        enabled = bool(agent.get("enabled"), enabled);
        storage.apply(map(yaml.get("storage")));
        kafka.apply(map(yaml.get("kafka")));
        http.apply(map(yaml.get("http")));
        redis.apply(map(yaml.get("redis")));
        mongo.apply(map(yaml.get("mongo")));
        payload.apply(map(yaml.get("payload")));
        redaction.apply(map(yaml.get("redaction")));
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

    private static boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
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

    public static class StorageConfig {
        private String type = "yaml";
        private String outputDir = "./recordings";
        private String fileStrategy = "trace-per-file";

        private void apply(Map<String, Object> map) {
            type = string(map.get("type"), type);
            outputDir = string(map.get("outputDir"), outputDir);
            fileStrategy = string(map.get("fileStrategy"), fileStrategy);
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
}
