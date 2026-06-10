package com.autogen.agent.instrumentation.redis;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.util.ReflectionUtils;
import net.bytebuddy.asm.Advice;

import java.util.LinkedHashMap;
import java.util.Map;

public class LettuceAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        TraceContext.currentOrCreate("redis");
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.AllArguments Object[] args,
                            @Advice.Thrown Throwable throwable) {
        Object command = args.length > 1 ? args[1] : args.length > 0 ? args[0] : null;
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("REDIS_COMMAND");
        interaction.setOperation(String.valueOf(ReflectionUtils.call(command, "getType")));
        interaction.setDurationMicros((System.nanoTime() - startedAt) / 1000L);
        interaction.setStatus(throwable == null ? "SENT" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client", "Lettuce");
        metadata.put("commandClass", command == null ? null : command.getClass().getName());
        metadata.put("arguments", String.valueOf(ReflectionUtils.call(command, "getArgs")));
        interaction.setMetadata(metadata);
        GlobalRecorder.get().record(interaction);
    }
}
