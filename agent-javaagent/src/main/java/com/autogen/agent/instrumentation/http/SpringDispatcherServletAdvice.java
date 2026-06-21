package com.autogen.agent.instrumentation.http;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.util.ReflectionUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class SpringDispatcherServletAdvice {
    public static final String RECORDED_ATTRIBUTE = "jvmTraceToTest.springDispatcherRecorded";

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static HttpScope enter(@Advice.Argument(value = 0, readOnly = false, typing = Assigner.Typing.DYNAMIC) Object request,
                                  @Advice.Argument(value = 1, readOnly = false, typing = Assigner.Typing.DYNAMIC) Object response) {
        Object wrappedRequest = contentCachingRequest(request);
        Object wrappedResponse = contentCachingResponse(response);
        request = wrappedRequest;
        response = wrappedResponse;

        Map<String, String> headers = ReflectionUtils.headersFromServletRequest(wrappedRequest);
        TraceContext context = TraceContext.fromHeaders(headers, "spring-dispatcher-servlet");
        return new HttpScope(System.nanoTime(), wrappedRequest, wrappedResponse, headers, TraceContext.open(context));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter HttpScope httpScope,
                            @Advice.Thrown Throwable throwable) {
        if (httpScope == null) {
            return;
        }
        try {
            Object path = ReflectionUtils.call(httpScope.request, "getRequestURI");
            if (!GlobalRecorder.get().config().getHttp().allows(String.valueOf(path))) {
                return;
            }

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
            metadata.put("framework", "Spring DispatcherServlet");
            metadata.put("path", path);
            metadata.put("query", ReflectionUtils.call(httpScope.request, "getQueryString"));
            metadata.put("remoteAddr", ReflectionUtils.call(httpScope.request, "getRemoteAddr"));
            metadata.put("scheme", ReflectionUtils.call(httpScope.request, "getScheme"));
            interaction.setMetadata(metadata);

            if (GlobalRecorder.get().config().getHttp().isCapturePayloads()) {
                interaction.setRequest(GlobalRecorder.get().redactor().payload(cachedBody(httpScope.request)));
                interaction.setResponse(GlobalRecorder.get().redactor().payload(cachedBody(httpScope.response)));
            }

            ReflectionUtils.call(httpScope.request, "setAttribute", new Class[]{String.class, Object.class}, RECORDED_ATTRIBUTE, Boolean.TRUE);
            GlobalRecorder.get().record(interaction);
        } finally {
            copyBodyToResponse(httpScope.response);
            httpScope.scope.close();
        }
    }

    private static Object contentCachingRequest(Object request) {
        return wrap(request, "org.springframework.web.util.ContentCachingRequestWrapper");
    }

    private static Object contentCachingResponse(Object response) {
        return wrap(response, "org.springframework.web.util.ContentCachingResponseWrapper");
    }

    private static Object wrap(Object target, String wrapperClassName) {
        if (target == null || target.getClass().getName().equals(wrapperClassName)) {
            return target;
        }
        ClassLoader[] classLoaders = new ClassLoader[]{
                target.getClass().getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                SpringDispatcherServletAdvice.class.getClassLoader()
        };
        for (ClassLoader classLoader : classLoaders) {
            try {
                Class<?> wrapperClass = Class.forName(wrapperClassName, false, classLoader);
                for (Constructor<?> constructor : wrapperClass.getConstructors()) {
                    Class<?>[] parameterTypes = constructor.getParameterTypes();
                    if (parameterTypes.length == 1 && parameterTypes[0].isInstance(target)) {
                        return constructor.newInstance(target);
                    }
                }
            } catch (Exception ignored) {
                // Try the next classloader.
            }
        }
        return target;
    }

    private static Object cachedBody(Object wrapper) {
        Object bytes = ReflectionUtils.call(wrapper, "getContentAsByteArray");
        if (!(bytes instanceof byte[])) {
            return null;
        }
        byte[] body = (byte[]) bytes;
        if (body.length == 0) {
            return null;
        }
        return new String(body, charset(wrapper));
    }

    private static Charset charset(Object requestOrResponse) {
        Object encoding = ReflectionUtils.call(requestOrResponse, "getCharacterEncoding");
        if (encoding != null) {
            try {
                return Charset.forName(String.valueOf(encoding));
            } catch (Exception ignored) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static void copyBodyToResponse(Object response) {
        ReflectionUtils.call(response, "copyBodyToResponse");
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
