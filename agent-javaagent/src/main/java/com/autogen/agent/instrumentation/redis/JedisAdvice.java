package com.autogen.agent.instrumentation.redis;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import net.bytebuddy.asm.Advice;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class JedisAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        TraceContext.currentOrCreate("redis");
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.AllArguments Object[] args,
                            @Advice.Thrown Throwable throwable) {
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("REDIS_COMMAND");
        interaction.setOperation(args.length > 0 ? String.valueOf(args[0]) : "UNKNOWN");
        interaction.setDurationMicros((System.nanoTime() - startedAt) / 1000L);
        interaction.setStatus(throwable == null ? "SENT" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client", "Jedis");
        metadata.put("arguments", args.length > 1 ? Arrays.deepToString(Arrays.copyOfRange(args, 1, args.length)) : "[]");
        interaction.setMetadata(metadata);
        GlobalRecorder.get().record(interaction);
    }
}
