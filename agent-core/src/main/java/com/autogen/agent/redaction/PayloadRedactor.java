package com.autogen.agent.redaction;

import com.autogen.agent.config.AgentConfig;
import com.autogen.agent.model.Payload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PayloadRedactor {
    private static final String REDACTED = "[REDACTED]";
    private final AgentConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PayloadRedactor(AgentConfig config) {
        this.config = config;
    }

    public Map<String, String> redactHeaders(Map<String, String> headers) {
        Set<String> redactedHeaders = config.getRedaction().getHeaders().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((name, value) -> result.put(name, redactedHeaders.contains(name.toLowerCase(Locale.ROOT)) ? REDACTED : value));
        return result;
    }

    public Payload payload(Object value) {
        if (value == null) {
            return Payload.text("null", null, false);
        }
        if (value instanceof byte[]) {
            return bytes((byte[]) value);
        }
        String text = String.valueOf(value);
        boolean truncated = false;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > config.getPayload().getMaxBytes()) {
            text = new String(bytes, 0, config.getPayload().getMaxBytes(), StandardCharsets.UTF_8);
            truncated = true;
        }
        Object body = tryJson(text);
        String format = body instanceof String ? "text" : "json";
        return Payload.text(format, body, truncated);
    }

    private Payload bytes(byte[] bytes) {
        int max = config.getPayload().getMaxBytes();
        boolean truncated = bytes.length > max;
        int size = Math.min(bytes.length, max);
        byte[] payload = new byte[size];
        System.arraycopy(bytes, 0, payload, 0, size);
        if ("base64".equalsIgnoreCase(config.getPayload().getBinaryPolicy())) {
            return new Payload("binary", "base64", Base64.getEncoder().encodeToString(payload), truncated);
        }
        return new Payload("binary", "omitted", "[BINARY_OMITTED]", truncated);
    }

    private Object tryJson(String text) {
        try {
            Object value = objectMapper.readValue(text, new TypeReference<Object>() {
            });
            redactJson(value);
            return value;
        } catch (Exception ignored) {
            return redactText(text);
        }
    }

    private String redactText(String text) {
        String redacted = text;
        for (String configured : config.getRedaction().getJsonFields()) {
            String field = configured;
            int dot = field.lastIndexOf('.');
            if (dot >= 0) {
                field = field.substring(dot + 1);
            }
            field = field.replace("$", "").replace("[*]", "");
            redacted = redacted.replaceAll("(?i)(" + java.util.regex.Pattern.quote(field) + "\\s*[=:]\\s*)[^,}\\]\\s]+", "$1" + REDACTED);
        }
        return redacted;
    }

    @SuppressWarnings("unchecked")
    private void redactJson(Object value) {
        if (value instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) value;
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if (shouldRedactField(name)) {
                    entry.setValue(REDACTED);
                } else {
                    redactJson(entry.getValue());
                }
            }
        } else if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                redactJson(item);
            }
        }
    }

    private boolean shouldRedactField(String field) {
        String normalized = field.toLowerCase(Locale.ROOT);
        for (String configured : config.getRedaction().getJsonFields()) {
            String candidate = configured;
            int dot = candidate.lastIndexOf('.');
            if (dot >= 0) {
                candidate = candidate.substring(dot + 1);
            }
            candidate = candidate.replace("$", "").replace("[*]", "").toLowerCase(Locale.ROOT);
            if (normalized.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
}
