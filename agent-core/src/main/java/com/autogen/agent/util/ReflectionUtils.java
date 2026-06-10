package com.autogen.agent.util;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReflectionUtils {
    private ReflectionUtils() {
    }

    public static Object call(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            Method declared = target.getClass().getMethod(method);
            declared.setAccessible(true);
            return declared.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Object call(Object target, String method, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method declared = target.getClass().getMethod(method, parameterTypes);
            declared.setAccessible(true);
            return declared.invoke(target, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String stringCall(Object target, String method) {
        Object value = call(target, method);
        return value == null ? null : String.valueOf(value);
    }

    public static Map<String, String> headersFromServletRequest(Object request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Object names = call(request, "getHeaderNames");
        if (names instanceof java.util.Enumeration) {
            java.util.Enumeration<?> enumeration = (java.util.Enumeration<?>) names;
            while (enumeration.hasMoreElements()) {
                String name = String.valueOf(enumeration.nextElement());
                Object value = call(request, "getHeader", new Class[]{String.class}, name);
                headers.put(name, value == null ? null : String.valueOf(value));
            }
        }
        return headers;
    }

    public static Map<String, String> kafkaHeaders(Object record) {
        Map<String, String> headers = new LinkedHashMap<>();
        Object kafkaHeaders = call(record, "headers");
        if (kafkaHeaders instanceof Iterable) {
            for (Object header : (Iterable<?>) kafkaHeaders) {
                String key = stringCall(header, "key");
                Object value = call(header, "value");
                if (key != null) {
                    headers.put(key, decodeHeaderValue(value));
                }
            }
        }
        return headers;
    }

    private static String decodeHeaderValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = (byte) Array.get(value, i);
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }
}
