package com.autogen.agent.sink;

import com.autogen.agent.api.RecordingSink;
import com.autogen.agent.config.AgentConfig;
import com.autogen.agent.model.RecordedTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

public class KafkaRecordingSink implements RecordingSink {
    private final String topic;
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaRecordingSink(AgentConfig.KafkaSinkConfig config) {
        this.topic = config.getTopic();
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, config.getClientId());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, config.getAcks());
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.getCompressionType());
        this.producer = new KafkaProducer<>(properties);
    }

    @Override
    public void write(RecordedTrace trace) {
        try {
            Map<String, Object> payload = TraceYamlMapper.toYamlMap(trace);
            String value = objectMapper.writeValueAsString(payload);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, trace.getTraceId(), value);
            record.headers().add("recording-format", "json".getBytes(StandardCharsets.UTF_8));
            record.headers().add("recording-service", nullSafe(trace.getService()).getBytes(StandardCharsets.UTF_8));
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("[traffic-recorder-agent] failed to write trace to kafka: " + exception.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("[traffic-recorder-agent] failed to serialize trace for kafka sink: " + e.getMessage());
        }
    }

    @Override
    public void flush() {
        producer.flush();
    }

    @Override
    public void close() {
        producer.flush();
        producer.close();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
