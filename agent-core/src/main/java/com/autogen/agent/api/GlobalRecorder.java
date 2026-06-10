package com.autogen.agent.api;

import com.autogen.agent.config.AgentConfig;
import com.autogen.agent.context.TraceContext;
import com.autogen.agent.model.RecordedInteraction;
import com.autogen.agent.model.RecordedTrace;
import com.autogen.agent.redaction.PayloadRedactor;
import com.autogen.agent.sink.RecordingSinks;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalRecorder {
    private static volatile GlobalRecorder INSTANCE = disabled();
    private static final ThreadLocal<Boolean> RECORDING_SUPPRESSED = ThreadLocal.withInitial(() -> false);

    private final AgentConfig config;
    private final RecordingSink sink;
    private final PayloadRedactor redactor;
    private final boolean active;
    private final Map<String, RecordedTrace> traces = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor writerExecutor;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicLong droppedInteractions = new AtomicLong();

    private GlobalRecorder(AgentConfig config, RecordingSink sink) {
        this(config, sink, true);
    }

    private GlobalRecorder(AgentConfig config, RecordingSink sink, boolean active) {
        this.config = config;
        this.sink = sink;
        this.active = active;
        this.redactor = new PayloadRedactor(config);
        this.writerExecutor = active ? createWriterExecutor(config) : null;
    }

    public static void initialize(AgentConfig config) {
        if (!config.isEnabled()) {
            INSTANCE = disabled();
            return;
        }
        INSTANCE = new GlobalRecorder(config, RecordingSinks.create(config));
    }

    public static GlobalRecorder get() {
        return INSTANCE;
    }

    private static GlobalRecorder disabled() {
        AgentConfig config = new AgentConfig();
        return new GlobalRecorder(config, trace -> {
        }, false);
    }

    public AgentConfig config() {
        return config;
    }

    public PayloadRedactor redactor() {
        return redactor;
    }

    public void record(RecordedInteraction interaction) {
        if (!active || !config.isEnabled() || interaction == null || shutdown.get() || isRecordingSuppressed()) {
            return;
        }
        TraceContext context = TraceContext.currentOrCreate(interaction.getType());
        interaction.setTraceId(context.getTraceId());
        interaction.setParentSpanId(context.getParentSpanId());
        interaction.setSpanId(TraceContext.newSpanId());
        interaction.setId(interaction.getTraceId() + "-" + interaction.getSpanId());
        interaction.setTimestamp(Instant.now());

        if (writerExecutor == null) {
            recordOnWriter(interaction);
            return;
        }
        try {
            writerExecutor.execute(() -> recordOnWriter(interaction));
        } catch (RejectedExecutionException e) {
            long dropped = droppedInteractions.incrementAndGet();
            if (dropped == 1 || dropped % 1000 == 0) {
                System.err.println("[traffic-recorder-agent] dropped recording interactions because async queue is full. dropped=" + dropped);
            }
        }
    }

    private void recordOnWriter(RecordedInteraction interaction) {
        RecordedTrace trace = traces.computeIfAbsent(interaction.getTraceId(), traceId -> new RecordedTrace(config.getServiceName(), traceId));
        synchronized (trace) {
            if (trace.getRootType() == null) {
                trace.setRootType(interaction.getType());
                trace.getRoot().putAll(interaction.getMetadata());
            }
            trace.getInteractions().add(interaction);
            withRecordingSuppressed(() -> sink.write(trace));
        }
    }

    public void flush() {
        if (writerExecutor != null && !shutdown.get()) {
            try {
                Future<?> barrier = writerExecutor.submit(() -> {
                });
                barrier.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | RejectedExecutionException ignored) {
                // Best-effort flush during overload or shutdown.
            }
        }
        withRecordingSuppressed(sink::flush);
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        if (writerExecutor != null) {
            writerExecutor.shutdown();
            try {
                if (!writerExecutor.awaitTermination(config.getAsync().getShutdownDrainMillis(), TimeUnit.MILLISECONDS)) {
                    System.err.println("[traffic-recorder-agent] async writer did not fully drain before shutdown");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        withRecordingSuppressed(sink::close);
    }

    private static boolean isRecordingSuppressed() {
        return Boolean.TRUE.equals(RECORDING_SUPPRESSED.get());
    }

    private static void withRecordingSuppressed(Runnable runnable) {
        boolean previous = isRecordingSuppressed();
        RECORDING_SUPPRESSED.set(true);
        try {
            runnable.run();
        } finally {
            RECORDING_SUPPRESSED.set(previous);
        }
    }

    private ThreadPoolExecutor createWriterExecutor(AgentConfig config) {
        if (!config.getAsync().isEnabled()) {
            return null;
        }
        int workerThreads = Math.max(1, config.getAsync().getWorkerThreads());
        int queueSize = Math.max(1, config.getAsync().getQueueSize());
        return new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                runnable -> {
                    Thread thread = new Thread(runnable, "traffic-recorder-writer");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
