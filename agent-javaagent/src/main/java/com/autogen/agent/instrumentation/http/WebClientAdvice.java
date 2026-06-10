package com.autogen.agent.instrumentation.http;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.util.ReflectionUtils;
import net.bytebuddy.asm.Advice;

import java.util.LinkedHashMap;
import java.util.Map;

public class WebClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        TraceContext.currentOrCreate("spring-webclient");
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.AllArguments Object[] args,
                            @Advice.Thrown Throwable throwable) {
        Object request = args.length > 0 ? args[0] : null;
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("HTTP_CLIENT");
        interaction.setOperation(String.valueOf(ReflectionUtils.call(request, "method")));
        interaction.setDurationMicros((System.nanoTime() - startedAt) / 1000L);
        interaction.setStatus(throwable == null ? "PUBLISHED" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client", "Spring WebClient");
        String url = String.valueOf(ReflectionUtils.call(request, "url"));
        if (!GlobalRecorder.get().config().getHttp().allows(url)) {
            return;
        }
        metadata.put("url", url);
        interaction.setMetadata(metadata);
        GlobalRecorder.get().record(interaction);
    }
}
