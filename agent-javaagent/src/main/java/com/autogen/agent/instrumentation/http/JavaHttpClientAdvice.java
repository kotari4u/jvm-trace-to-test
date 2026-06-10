package com.autogen.agent.instrumentation.http;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.util.ReflectionUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.util.LinkedHashMap;
import java.util.Map;

public class JavaHttpClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        TraceContext.currentOrCreate("java-http-client");
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.AllArguments Object[] args,
                            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returned,
                            @Advice.Thrown Throwable throwable) {
        Object request = args.length > 0 ? args[0] : null;
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("HTTP_CLIENT");
        interaction.setOperation(String.valueOf(ReflectionUtils.call(request, "method")));
        interaction.setDurationMicros((System.nanoTime() - startedAt) / 1000L);
        interaction.setStatus(throwable == null ? "COMPLETED" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client", "java.net.http.HttpClient");
        String uri = String.valueOf(ReflectionUtils.call(request, "uri"));
        if (!GlobalRecorder.get().config().getHttp().allows(uri)) {
            return;
        }
        metadata.put("uri", uri);
        metadata.put("statusCode", ReflectionUtils.call(returned, "statusCode"));
        interaction.setMetadata(metadata);
        if (GlobalRecorder.get().config().getHttp().isCapturePayloads()) {
            interaction.setResponse(GlobalRecorder.get().redactor().payload(ReflectionUtils.call(returned, "body")));
        }
        GlobalRecorder.get().record(interaction);
    }
}
