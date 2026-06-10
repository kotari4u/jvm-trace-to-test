package com.autogen.agent.instrumentation.http;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import net.bytebuddy.asm.Advice;

import java.util.LinkedHashMap;
import java.util.Map;

public class RestTemplateAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        TraceContext.currentOrCreate("http-client");
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.AllArguments Object[] args,
                            @Advice.Return Object returned,
                            @Advice.Thrown Throwable throwable) {
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("HTTP_CLIENT");
        interaction.setOperation(args.length > 1 ? String.valueOf(args[1]) : "UNKNOWN");
        interaction.setDurationMicros((System.nanoTime() - startedAt) / 1000L);
        interaction.setStatus(throwable == null ? "COMPLETED" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client", "RestTemplate");
        String url = args.length > 0 ? String.valueOf(args[0]) : null;
        if (!GlobalRecorder.get().config().getHttp().allows(url)) {
            return;
        }
        metadata.put("url", url);
        metadata.put("responseType", returned == null ? null : returned.getClass().getName());
        interaction.setMetadata(metadata);
        if (GlobalRecorder.get().config().getHttp().isCapturePayloads()) {
            interaction.setResponse(GlobalRecorder.get().redactor().payload(returned));
        }
        GlobalRecorder.get().record(interaction);
    }
}
