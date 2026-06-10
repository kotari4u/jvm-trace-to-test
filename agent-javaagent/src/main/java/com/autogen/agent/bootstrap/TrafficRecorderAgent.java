package com.autogen.agent.bootstrap;

import com.autogen.agent.api.GlobalRecorder;
import com.autogen.agent.config.AgentConfig;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

public final class TrafficRecorderAgent {
    private TrafficRecorderAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        start(agentArgs, instrumentation);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        start(agentArgs, instrumentation);
    }

    private static void start(String agentArgs, Instrumentation instrumentation) {
        Map<String, String> args = parseArgs(agentArgs);
        AgentConfig config = AgentConfig.load(args.get("config"));
        GlobalRecorder.initialize(config);
        AgentInstaller.install(instrumentation, config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> GlobalRecorder.get().shutdown(), "traffic-recorder-shutdown"));
        System.out.println("[traffic-recorder-agent] started for service=" + config.getServiceName());
    }

    private static Map<String, String> parseArgs(String agentArgs) {
        Map<String, String> result = new HashMap<>();
        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            return result;
        }
        String[] tokens = agentArgs.split(",");
        for (String token : tokens) {
            int equals = token.indexOf('=');
            if (equals > 0) {
                result.put(token.substring(0, equals).trim(), token.substring(equals + 1).trim());
            }
        }
        return result;
    }
}
