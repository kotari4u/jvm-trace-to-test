package com.autogen.agent.instrumentation.kafka;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.util.ReflectionUtils;
import net.bytebuddy.asm.Advice;

import java.util.LinkedHashMap;
import java.util.Map;

public class SpringKafkaListenerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ListenerScope enter(@Advice.AllArguments Object[] args) {
        Object message = args.length == 0 ? null : args[0];
        Object record = unwrapConsumerRecord(message);
        Map<String, String> headers = ReflectionUtils.kafkaHeaders(record);
        TraceContext context = TraceContext.fromHeaders(headers, "spring-kafka-listener");
        return new ListenerScope(System.nanoTime(), message, record, headers, TraceContext.open(context));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ListenerScope listenerScope,
                            @Advice.Thrown Throwable throwable) {
        if (listenerScope == null) {
            return;
        }
        try {
            RecordedInteraction interaction = new RecordedInteraction();
            interaction.setType("SPRING_KAFKA_LISTENER");
            interaction.setOperation("onMessage");
            interaction.setDurationMicros((System.nanoTime() - listenerScope.startedAt) / 1000L);
            interaction.setHeaders(GlobalRecorder.get().redactor().redactHeaders(listenerScope.headers));
            interaction.setStatus(throwable == null ? "PROCESSED" : "ERROR");
            if (throwable != null) {
                interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            Object topic = ReflectionUtils.call(listenerScope.record, "topic");
            if (!GlobalRecorder.get().config().getKafka().allows(String.valueOf(topic))) {
                return;
            }
            metadata.put("topic", topic);
            metadata.put("partition", ReflectionUtils.call(listenerScope.record, "partition"));
            metadata.put("offset", ReflectionUtils.call(listenerScope.record, "offset"));
            metadata.put("key", ReflectionUtils.call(listenerScope.record, "key"));
            interaction.setMetadata(metadata);
            Object payload = listenerScope.record == null ? ReflectionUtils.call(listenerScope.message, "getPayload") : ReflectionUtils.call(listenerScope.record, "value");
            if (GlobalRecorder.get().config().getKafka().isCapturePayloads()) {
                interaction.setRequest(GlobalRecorder.get().redactor().payload(payload));
            }
            GlobalRecorder.get().record(interaction);
        } finally {
            listenerScope.scope.close();
        }
    }

    private static Object unwrapConsumerRecord(Object message) {
        if (message == null) {
            return null;
        }
        if (message.getClass().getName().equals("org.apache.kafka.clients.consumer.ConsumerRecord")) {
            return message;
        }
        Object payload = ReflectionUtils.call(message, "getPayload");
        if (payload != null && payload.getClass().getName().equals("org.apache.kafka.clients.consumer.ConsumerRecord")) {
            return payload;
        }
        return null;
    }

    public static final class ListenerScope {
        private final long startedAt;
        private final Object message;
        private final Object record;
        private final Map<String, String> headers;
        private final TraceContext.Scope scope;

        ListenerScope(long startedAt, Object message, Object record, Map<String, String> headers, TraceContext.Scope scope) {
            this.startedAt = startedAt;
            this.message = message;
            this.record = record;
            this.headers = headers;
            this.scope = scope;
        }
    }
}
