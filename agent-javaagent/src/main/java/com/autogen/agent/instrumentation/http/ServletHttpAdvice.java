package com.autogen.agent.instrumentation.http;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.util.ReflectionUtils;
import net.bytebuddy.asm.Advice;

import java.util.LinkedHashMap;
import java.util.Map;

public class ServletHttpAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static HttpScope enter(@Advice.AllArguments Object[] args) {
        Object request = args.length > 0 ? args[0] : null;
        Object response = args.length > 1 ? args[1] : null;
        Map<String, String> headers = ReflectionUtils.headersFromServletRequest(request);
        TraceContext context = TraceContext.fromHeaders(headers, "http-server");
        return new HttpScope(System.nanoTime(), request, response, headers, TraceContext.open(context));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter HttpScope httpScope,
                            @Advice.Thrown Throwable throwable) {
        if (httpScope == null) {
            return;
        }
        try {
            RecordedInteraction interaction = new RecordedInteraction();
            interaction.setType("HTTP_SERVER");
            interaction.setOperation(value(ReflectionUtils.call(httpScope.request, "getMethod"), "UNKNOWN"));
            interaction.setDurationMicros((System.nanoTime() - httpScope.startedAt) / 1000L);
            interaction.setHeaders(GlobalRecorder.get().redactor().redactHeaders(httpScope.headers));
            interaction.setStatus(throwable == null ? String.valueOf(ReflectionUtils.call(httpScope.response, "getStatus")) : "ERROR");
            if (throwable != null) {
                interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            Object path = ReflectionUtils.call(httpScope.request, "getRequestURI");
            if (!GlobalRecorder.get().config().getHttp().allows(String.valueOf(path))) {
                return;
            }
            metadata.put("path", path);
            metadata.put("query", ReflectionUtils.call(httpScope.request, "getQueryString"));
            metadata.put("remoteAddr", ReflectionUtils.call(httpScope.request, "getRemoteAddr"));
            metadata.put("scheme", ReflectionUtils.call(httpScope.request, "getScheme"));
            interaction.setMetadata(metadata);
            GlobalRecorder.get().record(interaction);
        } finally {
            httpScope.scope.close();
        }
    }

    private static String value(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    public static final class HttpScope {
        private final long startedAt;
        private final Object request;
        private final Object response;
        private final Map<String, String> headers;
        private final TraceContext.Scope scope;

        HttpScope(long startedAt, Object request, Object response, Map<String, String> headers, TraceContext.Scope scope) {
            this.startedAt = startedAt;
            this.request = request;
            this.response = response;
            this.headers = headers;
            this.scope = scope;
        }
    }
}
