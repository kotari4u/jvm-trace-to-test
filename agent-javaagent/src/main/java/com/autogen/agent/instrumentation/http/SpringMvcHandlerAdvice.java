package com.autogen.agent.instrumentation.http;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.util.ReflectionUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class SpringMvcHandlerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        TraceContext.currentOrCreate("spring-mvc");
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter long startedAt,
                            @Advice.This Object handlerMethod,
                            @Advice.AllArguments Object[] args,
                            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returned,
                            @Advice.Thrown Throwable throwable) {
        RecordedInteraction interaction = new RecordedInteraction();
        interaction.setType("SPRING_MVC_HANDLER");
        interaction.setOperation(String.valueOf(ReflectionUtils.call(handlerMethod, "getMethod")));
        interaction.setDurationMicros((System.nanoTime() - startedAt) / 1000L);
        interaction.setStatus(throwable == null ? "COMPLETED" : "ERROR");
        if (throwable != null) {
            interaction.setError(new RecordedInteraction.ErrorInfo(throwable));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("beanType", String.valueOf(ReflectionUtils.call(handlerMethod, "getBeanType")));
        metadata.put("handler", String.valueOf(ReflectionUtils.call(handlerMethod, "getMethod")));
        interaction.setMetadata(metadata);

        if (GlobalRecorder.get().config().getHttp().isCapturePayloads()) {
            Object[] controllerArgs = extractControllerArgs(args);
            interaction.setRequest(GlobalRecorder.get().redactor().payload(Arrays.deepToString(controllerArgs)));
            interaction.setResponse(GlobalRecorder.get().redactor().payload(returned));
        }
        GlobalRecorder.get().record(interaction);
    }

    private static Object[] extractControllerArgs(Object[] adviceArgs) {
        if (adviceArgs == null || adviceArgs.length == 0 || !(adviceArgs[0] instanceof Object[])) {
            return new Object[0];
        }
        return (Object[]) adviceArgs[0];
    }
}
