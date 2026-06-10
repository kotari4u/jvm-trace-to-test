package com.autogen.agent.sink;

import com.autogen.agent.api.RecordingSink;
import com.autogen.agent.config.AgentConfig;

public final class RecordingSinks {
    private RecordingSinks() {
    }

    public static RecordingSink create(AgentConfig config) {
        String type = config.getStorage().getType();
        if ("memory".equalsIgnoreCase(type) || "in-memory".equalsIgnoreCase(type)) {
            return new InMemoryRecordingSink();
        }
        if ("yaml".equalsIgnoreCase(type)) {
            return new YamlFileRecordingSink(config.getStorage().getOutputDir());
        }
        throw new IllegalArgumentException("Unsupported recording sink type: " + type);
    }
}
