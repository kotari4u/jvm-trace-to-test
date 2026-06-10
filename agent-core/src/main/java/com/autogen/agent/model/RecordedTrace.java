package com.autogen.agent.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecordedTrace {
    private int version = 1;
    private String service;
    private String traceId;
    private Instant recordedAt;
    private String rootType;
    private Map<String, Object> root = new LinkedHashMap<>();
    private List<RecordedInteraction> interactions = new ArrayList<>();

    public RecordedTrace() {
    }

    public RecordedTrace(String service, String traceId) {
        this.service = service;
        this.traceId = traceId;
        this.recordedAt = Instant.now();
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getRootType() {
        return rootType;
    }

    public void setRootType(String rootType) {
        this.rootType = rootType;
    }

    public Map<String, Object> getRoot() {
        return root;
    }

    public void setRoot(Map<String, Object> root) {
        this.root = root;
    }

    public List<RecordedInteraction> getInteractions() {
        return interactions;
    }

    public void setInteractions(List<RecordedInteraction> interactions) {
        this.interactions = interactions;
    }
}
