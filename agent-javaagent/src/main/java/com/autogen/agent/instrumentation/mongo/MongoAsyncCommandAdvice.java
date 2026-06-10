package com.autogen.agent.instrumentation.mongo;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import net.bytebuddy.asm.Advice;

import java.util.LinkedHashMap;
import java.util.Map;

public class MongoAsyncCommandAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        TraceContext.currentOrCreate("mongo");
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.This(optional = true) Object target,
                            @Advice.AllArguments Object[] args,
                            @Advice.Thrown Throwable throwable) {
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("MONGO_COMMAND");
        interaction.setOperation(target == null ? "executeAsync" : target.getClass().getSimpleName());
        interaction.setDurationMicros((System.nanoTime() - startedAt) / 1000L);
        interaction.setStatus(throwable == null ? "SUBMITTED" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("driverClass", target == null ? null : target.getClass().getName());
        metadata.put("argumentCount", args == null ? 0 : args.length);
        metadata.put("async", true);
        interaction.setMetadata(metadata);
        GlobalRecorder.get().record(interaction);
    }
}
