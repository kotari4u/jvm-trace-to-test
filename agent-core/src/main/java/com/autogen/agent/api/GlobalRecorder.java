package com.autogen.agent.api;

import com.autogen.agent.config.AgentConfig;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.model.RecordedTrace;
import com.autogen.agent.redaction.PayloadRedactor;
import com.autogen.agent.sink.RecordingSinks;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalRecorder {
    private static volatile GlobalRecorder INSTANCE = disabled();

    private final AgentConfig config;
    private final RecordingSink sink;
    private final PayloadRedactor redactor;
    private final Map<String, RecordedTrace> traces = new ConcurrentHashMap<>();

    private GlobalRecorder(AgentConfig config, RecordingSink sink) {
        this.config = config;
        this.sink = sink;
        this.redactor = new PayloadRedactor(config);
    }

    public static void initialize(AgentConfig config) {
        if (!config.isEnabled()) {
            INSTANCE = disabled();
            return;
        }
        INSTANCE = new GlobalRecorder(config, RecordingSinks.create(config));
    }

    public static GlobalRecorder get() {
        return INSTANCE;
    }

    private static GlobalRecorder disabled() {
        AgentConfig config = new AgentConfig();
        return new GlobalRecorder(config, trace -> {
        });
    }

    public AgentConfig config() {
        return config;
    }

    public PayloadRedactor redactor() {
        return redactor;
    }

    public void record(RecordedInteraction interaction) {
        if (!config.isEnabled() || interaction == null) {
            return;
        }
        TraceContext context = TraceContext.currentOrCreate(interaction.getType());
        interaction.setTraceId(context.getTraceId());
        interaction.setParentSpanId(context.getParentSpanId());
        interaction.setSpanId(TraceContext.newSpanId());
        interaction.setId(interaction.getTraceId() + "-" + interaction.getSpanId());
        interaction.setTimestamp(Instant.now());
        RecordedTrace trace = traces.computeIfAbsent(context.getTraceId(), traceId -> new RecordedTrace(config.getServiceName(), traceId));
        synchronized (trace) {
            if (trace.getRootType() == null) {
                trace.setRootType(interaction.getType());
                trace.getRoot().putAll(interaction.getMetadata());
            }
            trace.getInteractions().add(interaction);
            sink.write(trace);
        }
    }

    public void flush() {
        sink.flush();
    }
}
