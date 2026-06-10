package com.autogen.agent.context;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TraceContext {
    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String source;

    private TraceContext(String traceId, String spanId, String parentSpanId, String source) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.source = source;
    }

    public static TraceContext currentOrCreate(String source) {
        TraceContext current = CURRENT.get();
        if (current != null) {
            return current;
        }
        TraceContext created = root(newTraceId(), source);
        CURRENT.set(created);
        return created;
    }

    public static TraceContext root(String traceId, String source) {
        return new TraceContext(traceId == null || traceId.isEmpty() ? newTraceId() : traceId, newSpanId(), null, source);
    }

    public static Scope open(TraceContext context) {
        TraceContext previous = CURRENT.get();
        CURRENT.set(context);
        return new Scope(previous);
    }

    public static TraceContext current() {
        return CURRENT.get();
    }

    public static TraceContext fromHeaders(Map<String, String> headers, String source) {
        if (headers == null || headers.isEmpty()) {
            return root(newTraceId(), source);
        }
        String traceId = first(headers, "traceparent", "x-correlation-id", "x-request-id");
        if (traceId != null && traceId.startsWith("00-")) {
            String[] parts = traceId.split("-");
            if (parts.length >= 2) {
                traceId = parts[1];
            }
        }
        return root(traceId == null ? newTraceId() : traceId, source);
    }

    private static String first(Map<String, String> headers, String... names) {
        for (String name : names) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String newTraceId() {
        return "rec-" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public String getSource() {
        return source;
    }

    public static final class Scope implements AutoCloseable {
        private final TraceContext previous;

        private Scope(TraceContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
