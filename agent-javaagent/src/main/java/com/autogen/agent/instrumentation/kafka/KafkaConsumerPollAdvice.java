package com.autogen.agent.instrumentation.kafka;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.util.ReflectionUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.util.LinkedHashMap;
import java.util.Map;

public class KafkaConsumerPollAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object records,
                            @Advice.Thrown Throwable throwable) {
        if (records instanceof Iterable) {
            for (Object record : (Iterable<?>) records) {
                recordConsumerRecord(startedAt, record, throwable);
            }
        } else if (throwable != null) {
            RecordedInteraction interaction = new RecordedInteraction();
            interaction.setType("KAFKA_CONSUME");
            interaction.setOperation("poll");
            interaction.setDurationMicros(durationMicros(startedAt));
            interaction.setStatus("ERROR");
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
            GlobalRecorder.get().record(interaction);
        }
    }

    private static void recordConsumerRecord(long startedAt, Object record, Throwable throwable) {
        Map<String, String> headers = ReflectionUtils.kafkaHeaders(record);
        TraceContext context = TraceContext.fromHeaders(headers, "kafka-consumer");
        try (TraceContext.Scope ignored = TraceContext.open(context)) {
            Object topic = ReflectionUtils.call(record, "topic");
            if (!GlobalRecorder.get().config().getKafka().allows(String.valueOf(topic))) {
                return;
            }
            RecordedInteraction interaction = new RecordedInteraction();
            interaction.setType("KAFKA_CONSUME");
            interaction.setOperation("poll");
            interaction.setDurationMicros(durationMicros(startedAt));
            interaction.setHeaders(GlobalRecorder.get().redactor().redactHeaders(headers));
            interaction.setStatus(throwable == null ? "RECEIVED" : "ERROR");
            if (throwable != null) {
                interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("topic", topic);
            metadata.put("partition", ReflectionUtils.call(record, "partition"));
            metadata.put("offset", ReflectionUtils.call(record, "offset"));
            metadata.put("timestamp", ReflectionUtils.call(record, "timestamp"));
            metadata.put("key", ReflectionUtils.call(record, "key"));
            interaction.setMetadata(metadata);

            if (GlobalRecorder.get().config().getKafka().isCapturePayloads()) {
                interaction.setRequest(GlobalRecorder.get().redactor().payload(ReflectionUtils.call(record, "value")));
            }
            GlobalRecorder.get().record(interaction);
        }
    }

    private static long durationMicros(long startedAt) {
        return (System.nanoTime() - startedAt) / 1000L;
    }
}
