package com.autogen.agent.api;

import com.autogen.agent.model.RecordedTrace;

public interface RecordingSink extends AutoCloseable {
    void write(RecordedTrace trace);

    default void flush() {
    }

    @Override
    default void close() {
        flush();
    }
}
