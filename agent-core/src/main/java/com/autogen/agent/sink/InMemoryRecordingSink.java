package com.autogen.agent.sink;

import com.autogen.agent.api.RecordingSink;
import com.autogen.agent.model.RecordedTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryRecordingSink implements RecordingSink {
    private final int maxTraces;
    private final List<RecordedTrace> traces = Collections.synchronizedList(new ArrayList<>());

    public InMemoryRecordingSink() {
        this(1000);
    }

    public InMemoryRecordingSink(int maxTraces) {
        this.maxTraces = maxTraces;
    }

    @Override
    public void write(RecordedTrace trace) {
        synchronized (traces) {
            traces.removeIf(existing -> existing.getTraceId().equals(trace.getTraceId()));
            traces.add(trace);
            while (traces.size() > maxTraces) {
                traces.remove(0);
            }
        }
    }

    public List<RecordedTrace> snapshot() {
        synchronized (traces) {
            return new ArrayList<>(traces);
        }
    }
}
