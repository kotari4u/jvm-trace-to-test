package com.autogen.agent.instrumentation.spring;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.model.RecordedInteraction;
import net.bytebuddy.asm.Advice;

import java.util.LinkedHashMap;
import java.util.Map;

public class SpringBootLifecycleAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.Thrown Throwable throwable) {
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("SPRING_BOOT");
        interaction.setOperation("SpringApplication.run");
        interaction.setDurationMicros((System.nanoTime() - startedAt) / 1000L);
        interaction.setStatus(throwable == null ? "STARTED" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("support", "spring-boot-first-class");
        interaction.setMetadata(metadata);
        GlobalRecorder.get().record(interaction);
    }
}
