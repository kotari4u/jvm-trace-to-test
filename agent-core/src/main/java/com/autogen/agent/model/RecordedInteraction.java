package com.autogen.agent.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class RecordedInteraction {
    private String id;
    private String traceId;
    private String parentSpanId;
    private String spanId;
    private Instant timestamp;
    private String type;
    private String operation;
    private long durationMicros;
    private String status;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private Map<String, String> headers = new LinkedHashMap<>();
    private Payload request;
    private Payload response;
    private ErrorInfo error;

    public RecordedInteraction() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public long getDurationMicros() {
        return durationMicros;
    }

    public void setDurationMicros(long durationMicros) {
        this.durationMicros = durationMicros;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Payload getRequest() {
        return request;
    }

    public void setRequest(Payload request) {
        this.request = request;
    }

    public Payload getResponse() {
        return response;
    }

    public void setResponse(Payload response) {
        this.response = response;
    }

    public ErrorInfo getError() {
        return error;
    }

    public void setError(ErrorInfo error) {
        this.error = error;
    }

    public static class ErrorInfo {
        private String type;
        private String message;

        public ErrorInfo() {
        }

        public ErrorInfo(Throwable throwable) {
            this.type = throwable.getClass().getName();
            this.message = throwable.getMessage();
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
