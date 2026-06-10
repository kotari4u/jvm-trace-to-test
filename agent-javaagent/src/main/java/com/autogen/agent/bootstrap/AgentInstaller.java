package com.autogen.agent.bootstrap;

import com.autogen.agent.config.AgentConfig;
import com.autogen.agent.instrumentation.http.JavaHttpClientAdvice;
import com.autogen.agent.instrumentation.http.RestTemplateAdvice;
import com.autogen.agent.instrumentation.http.ServletHttpAdvice;
import com.autogen.agent.instrumentation.http.SpringMvcHandlerAdvice;
import com.autogen.agent.instrumentation.http.WebClientAdvice;
import com.autogen.agent.instrumentation.kafka.KafkaConsumerPollAdvice;
import com.autogen.agent.instrumentation.kafka.KafkaProducerSendAdvice;
import com.autogen.agent.instrumentation.kafka.SpringKafkaListenerAdvice;
import com.autogen.agent.instrumentation.mongo.MongoAsyncCommandAdvice;
import com.autogen.agent.instrumentation.mongo.MongoCommandAdvice;
import com.autogen.agent.instrumentation.mongo.SpringMongoTemplateAdvice;
import com.autogen.agent.instrumentation.redis.JedisAdvice;
import com.autogen.agent.instrumentation.redis.LettuceAdvice;
import com.autogen.agent.instrumentation.redis.SpringRedisTemplateAdvice;
import com.autogen.agent.instrumentation.spring.SpringBootLifecycleAdvice;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class AgentInstaller {
    private AgentInstaller() {
    }

    public static void install(Instrumentation instrumentation, AgentConfig config) {
        AgentBuilder builder = new AgentBuilder.Default()
                .ignore(nameContains("net.bytebuddy.")
                        .or(nameContains("com.autogen.agent."))
                        .or(nameContains("org.yaml.snakeyaml."))
                        .or(nameContains("com.fasterxml.jackson.")))
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new LoggingListener());

        if (config.getKafka().isEnabled()) {
            builder = builder
                    .type(named("org.apache.kafka.clients.consumer.KafkaConsumer"))
                    .transform(advice(named("poll"), KafkaConsumerPollAdvice.class))
                    .type(named("org.apache.kafka.clients.producer.KafkaProducer"))
                    .transform(advice(named("send"), KafkaProducerSendAdvice.class))
                    .type(nameContains("org.springframework.kafka.listener.adapter"))
                    .transform(advice(named("onMessage").or(named("invokeHandler")), SpringKafkaListenerAdvice.class));
        }

        if (config.getHttp().isEnabled()) {
            builder = builder
                    .type(named("org.apache.catalina.core.ApplicationFilterChain"))
                    .transform(advice(named("doFilter"), ServletHttpAdvice.class))
                    .type(named("org.springframework.web.method.support.InvocableHandlerMethod"))
                    .transform(advice(named("doInvoke"), SpringMvcHandlerAdvice.class))
                    .type(named("org.springframework.web.client.RestTemplate"))
                    .transform(advice(named("doExecute"), RestTemplateAdvice.class))
                    .type(named("org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction"))
                    .transform(advice(named("exchange"), WebClientAdvice.class))
                    .type(named("jdk.internal.net.http.HttpClientImpl"))
                    .transform(advice(named("send").or(named("sendAsync")), JavaHttpClientAdvice.class));
        }

        if (config.getRedis().isEnabled()) {
            builder = builder
                    .type(named("org.springframework.data.redis.core.RedisTemplate"))
                    .transform(advice(named("execute").or(named("executePipelined")), SpringRedisTemplateAdvice.class))
                    .type(named("redis.clients.jedis.Connection"))
                    .transform(advice(named("sendCommand"), JedisAdvice.class))
                    .type(named("io.lettuce.core.protocol.CommandHandler"))
                    .transform(advice(named("write"), LettuceAdvice.class));
        }

        if (config.getMongo().isEnabled()) {
            builder = builder
                    .type(named("org.springframework.data.mongodb.core.MongoTemplate"))
                    .transform(advice(nameContains("find")
                            .or(nameContains("insert"))
                            .or(nameContains("save"))
                            .or(nameContains("update"))
                            .or(nameContains("remove"))
                            .or(nameContains("aggregate"))
                            .or(nameContains("count"))
                            .or(nameContains("exists"))), SpringMongoTemplateAdvice.class)
                    .type(nameContains("com.mongodb.internal.operation").or(nameContains("com.mongodb.internal.connection")))
                    .transform(advice(named("execute"), MongoCommandAdvice.class))
                    .type(nameContains("com.mongodb.internal.operation").or(nameContains("com.mongodb.internal.connection")))
                    .transform(advice(named("executeAsync"), MongoAsyncCommandAdvice.class));
        }

        builder
                .type(named("org.springframework.boot.SpringApplication"))
                .transform(advice(named("run").and(takesArguments(1)), SpringBootLifecycleAdvice.class))
                .installOn(instrumentation);
    }

    private static AgentBuilder.Transformer advice(ElementMatcher<? super MethodDescription> methodMatcher, Class<?> adviceClass) {
        return (builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(Advice.to(adviceClass).on(methodMatcher));
    }

    private static final class LoggingListener implements AgentBuilder.Listener {
        @Override
        public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
        }

        @Override
        public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
            System.out.println("[traffic-recorder-agent] instrumented " + typeDescription.getName());
        }

        @Override
        public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {
        }

        @Override
        public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
            System.err.println("[traffic-recorder-agent] instrumentation failed for " + typeName + ": " + throwable.getMessage());
        }

        @Override
        public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
        }
    }
}
