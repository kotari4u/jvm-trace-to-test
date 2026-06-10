package com.autogen.agent.sink;

import com.autogen.agent.api.RecordingSink;
import com.autogen.agent.model.RecordedTrace;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class YamlFileRecordingSink implements RecordingSink {
    private final Path outputDir;
    private final Yaml yaml;

    public YamlFileRecordingSink(String outputDir) {
        this.outputDir = Path.of(outputDir);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        this.yaml = new Yaml(options);
    }

    @Override
    public void write(RecordedTrace trace) {
        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve(sanitize(trace.getTraceId()) + ".yaml");
            try (Writer writer = Files.newBufferedWriter(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                yaml.dump(TraceYamlMapper.toYamlMap(trace), writer);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write recording trace " + trace.getTraceId(), e);
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
