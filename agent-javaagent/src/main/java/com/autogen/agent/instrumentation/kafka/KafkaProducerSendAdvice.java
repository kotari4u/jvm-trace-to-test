package com.autogen.agent.instrumentation.kafka;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.util.ReflectionUtils;
import net.bytebuddy.asm.Advice;

import java.util.LinkedHashMap;
import java.util.Map;

public class KafkaProducerSendAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        TraceContext.currentOrCreate("kafka-producer");
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.AllArguments Object[] args,
                            @Advice.Thrown Throwable throwable) {
        Object producerRecord = args.length == 0 ? null : args[0];
        Object topic = ReflectionUtils.call(producerRecord, "topic");
        if (!GlobalRecorder.get().config().getKafka().allows(String.valueOf(topic))) {
            return;
        }
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("KAFKA_PUBLISH");
        interaction.setOperation("send");
        interaction.setDurationMicros(durationMicros(startedAt));
        interaction.setStatus(throwable == null ? "SENT" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }

        Map<String, String> headers = ReflectionUtils.kafkaHeaders(producerRecord);
        interaction.setHeaders(GlobalRecorder.get().redactor().redactHeaders(headers));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("topic", topic);
        metadata.put("partition", ReflectionUtils.call(producerRecord, "partition"));
        metadata.put("timestamp", ReflectionUtils.call(producerRecord, "timestamp"));
        metadata.put("key", ReflectionUtils.call(producerRecord, "key"));
        interaction.setMetadata(metadata);

        if (GlobalRecorder.get().config().getKafka().isCapturePayloads()) {
            interaction.setRequest(GlobalRecorder.get().redactor().payload(ReflectionUtils.call(producerRecord, "value")));
        }
        GlobalRecorder.get().record(interaction);
    }

    private static long durationMicros(long startedAt) {
        return (System.nanoTime() - startedAt) / 1000L;
    }
}
