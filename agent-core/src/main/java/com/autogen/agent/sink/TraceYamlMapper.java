package com.autogen.agent.sink;

import com.autogen.agent.model.Payload;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.model.RecordedTrace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TraceYamlMapper {
    private TraceYamlMapper() {
    }

    static Map<String, Object> toYamlMap(RecordedTrace trace) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", trace.getVersion());
        value.put("service", trace.getService());
        value.put("recordedAt", String.valueOf(trace.getRecordedAt()));
        value.put("traceId", trace.getTraceId());
        value.put("rootType", trace.getRootType());
        value.put("root", trace.getRoot());
        List<Map<String, Object>> interactions = new ArrayList<>();
        for (RecordedInteraction interaction : trace.getInteractions()) {
            interactions.add(interaction(interaction));
        }
        value.put("interactions", interactions);
        return value;
    }

    private static Map<String, Object> interaction(RecordedInteraction interaction) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", interaction.getId());
        value.put("traceId", interaction.getTraceId());
        value.put("parentSpanId", interaction.getParentSpanId());
        value.put("spanId", interaction.getSpanId());
        value.put("timestamp", String.valueOf(interaction.getTimestamp()));
        value.put("type", interaction.getType());
        value.put("operation", interaction.getOperation());
        value.put("durationMicros", interaction.getDurationMicros());
        value.put("status", interaction.getStatus());
        value.put("metadata", interaction.getMetadata());
        value.put("headers", interaction.getHeaders());
        if (interaction.getRequest() != null) {
            value.put("request", payload(interaction.getRequest()));
        }
        if (interaction.getResponse() != null) {
            value.put("response", payload(interaction.getResponse()));
        }
        if (interaction.getError() != null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("type", interaction.getError().getType());
            error.put("message", interaction.getError().getMessage());
            value.put("error", error);
        }
        return value;
    }

    private static Map<String, Object> payload(Payload payload) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("format", payload.getFormat());
        value.put("encoding", payload.getEncoding());
        value.put("truncated", payload.isTruncated());
        value.put("body", payload.getBody());
        return value;
    }
}
