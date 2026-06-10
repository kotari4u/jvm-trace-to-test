package com.autogen.agent.instrumentation.redis;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class SpringRedisTemplateAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        TraceContext.currentOrCreate("spring-data-redis");
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.Origin("#m") String methodName,
                            @Advice.AllArguments Object[] args,
                            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returned,
                            @Advice.Thrown Throwable throwable) {
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("REDIS_COMMAND");
        interaction.setOperation(methodName);
        interaction.setDurationMicros((System.nanoTime() - startedAt) / 1000L);
        interaction.setStatus(throwable == null ? "COMPLETED" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client", "Spring RedisTemplate");
        metadata.put("argumentCount", args == null ? 0 : args.length);
        metadata.put("resultType", returned == null ? null : returned.getClass().getName());
        interaction.setMetadata(metadata);
        if (GlobalRecorder.get().config().getRedis().isCapturePayloads()) {
            interaction.setRequest(GlobalRecorder.get().redactor().payload(Arrays.deepToString(args)));
            interaction.setResponse(GlobalRecorder.get().redactor().payload(returned));
        }
        GlobalRecorder.get().record(interaction);
    }
}
