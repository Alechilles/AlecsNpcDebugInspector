package com.alechilles.alecsnpcdebuginspector.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Coordinates request queue processing and the bounded scenario runner.
 */
public final class NpcRuntimeHarnessService {
    private final NpcRuntimeHarnessConfig config;
    private final NpcRuntimeRequestQueue queue;
    private final ScenarioRunner runner;
    private final NpcRuntimeHarnessStatusWriter statusWriter;
    private final Supplier<NpcRuntimeWorldReadiness> worldReadinessSupplier;
    private final Supplier<NpcRuntimeWorldReadiness> worldReadinessPreparer;
    private final AtomicBoolean enabled;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile String activeRequestId;
    private volatile String lastResultId;
    private volatile String lastResultStatus;
    private volatile String lastResultClassification;
    private volatile boolean lastStatusWriteSucceeded;
    private volatile String lastStatusWriteError;
    private volatile NpcRuntimeWorldReadiness lastWorldReadiness;
    private volatile NpcRuntimeRecoveryReport lastRecoveryReport;
    private ScheduledExecutorService executor;

    public NpcRuntimeHarnessService(@Nonnull NpcRuntimeHarnessConfig config) {
        this(config, NpcRuntimeHarnessService::defaultDryRun);
    }

    public NpcRuntimeHarnessService(@Nonnull NpcRuntimeHarnessConfig config, @Nonnull ScenarioRunner runner) {
        this(config, runner, new NpcRuntimeFlatworldManager());
    }

    private NpcRuntimeHarnessService(@Nonnull NpcRuntimeHarnessConfig config,
                                     @Nonnull ScenarioRunner runner,
                                     @Nonnull NpcRuntimeFlatworldManager flatworldManager) {
        this(
                config,
                runner,
                new NpcRuntimeHarnessStatusWriter(config.paths()),
                () -> flatworldManager.currentReadiness(config.defaultWorldId()),
                () -> flatworldManager.ensureWorldReady(config.defaultWorldId())
        );
    }

    NpcRuntimeHarnessService(@Nonnull NpcRuntimeHarnessConfig config,
                             @Nonnull ScenarioRunner runner,
                             @Nonnull NpcRuntimeHarnessStatusWriter statusWriter,
                             @Nonnull Supplier<NpcRuntimeWorldReadiness> worldReadinessSupplier,
                             @Nonnull Supplier<NpcRuntimeWorldReadiness> worldReadinessPreparer) {
        this.config = config;
        this.queue = new NpcRuntimeRequestQueue(config.paths());
        this.runner = runner;
        this.statusWriter = statusWriter;
        this.worldReadinessSupplier = worldReadinessSupplier;
        this.worldReadinessPreparer = worldReadinessPreparer;
        this.enabled = new AtomicBoolean(config.initiallyEnabled());
        this.lastWorldReadiness = NpcRuntimeWorldReadiness.notReady(
                config.defaultWorldId(),
                NpcRuntimeWorldReadiness.WORLD_NOT_LOADED,
                "World readiness has not been checked yet"
        );
    }

    public void initializeDirectories() throws IOException {
        queue.ensureDirectories();
        recoverStaleActiveRequests("startup");
        writeStatus();
    }

    public void start() throws IOException {
        initializeDirectories();
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (config.autoEnable()) {
            prepareRuntimeWorldBestEffort();
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "npc-runtime-harness");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(
                this::pollSafely,
                config.pollIntervalMillis(),
                config.pollIntervalMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    public void shutdown() {
        ScheduledExecutorService service = executor;
        if (service != null) {
            service.shutdownNow();
            try {
                service.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        recoverStaleActiveRequestsBestEffort("shutdown");
        started.set(false);
        writeStatusBestEffort();
    }

    public boolean enabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        writeStatusBestEffort();
    }

    @Nullable
    public String activeRequestId() {
        return activeRequestId;
    }

    public long queuedCount() throws IOException {
        return queue.queuedCount();
    }

    @Nonnull
    public String statusText() {
        String active = activeRequestId != null ? activeRequestId : "<none>";
        String last = lastResultId != null ? lastResultId + ":" + lastResultStatus + ":" + lastResultClassification : "<none>";
        NpcRuntimeWorldReadiness worldReadiness = currentWorldReadiness();
        long queued;
        try {
            queued = queuedCount();
        } catch (IOException exception) {
                return "NPC Runtime Harness: enabled=" + enabled() + " active=" + active
                    + " queue=<error: " + exception.getMessage() + ">"
                    + " last=" + last
                    + " worldReady=" + worldReadiness.ready()
                    + " worldReason=" + worldReadiness.reason()
                    + " root=" + config.paths().root()
                    + " statusFile=" + statusWriter.statusFile()
                    + " statusWrite=" + statusWriteText();
        }
        return "NPC Runtime Harness: enabled=" + enabled()
                + " active=" + active
                + " queued=" + queued
                + " last=" + last
                + " worldReady=" + worldReadiness.ready()
                + " worldReason=" + worldReadiness.reason()
                + " root=" + config.paths().root()
                + " statusFile=" + statusWriter.statusFile()
                + " statusWrite=" + statusWriteText();
    }

    @Nonnull
    public String pathsText() {
        NpcRuntimePaths paths = config.paths();
        return "NPC Runtime Harness paths: root=" + paths.root()
                + " requests=" + paths.requests()
                + " results=" + paths.results()
                + " traces=" + paths.traces()
                + " archive=" + paths.archive()
                + " status=" + paths.status();
    }

    @Nonnull
    public NpcRuntimeHarnessConfig config() {
        return config;
    }

    @Nonnull
    public ProcessOutcome processNextQueuedRequest() throws IOException {
        Optional<NpcRuntimeRequestQueue.ActiveRequest> claimed = queue.claimNext();
        if (claimed.isEmpty()) {
            writeStatus();
            return new ProcessOutcome(false, null);
        }

        NpcRuntimeRequestQueue.ActiveRequest active = claimed.get();
        activeRequestId = active.requestId();
        writeStatusBestEffort();
        try {
            String text = Files.readString(active.path(), StandardCharsets.UTF_8);
            NpcRuntimeRequest request = NpcRuntimeRequest.parse(text, config);
            activeRequestId = request.requestId();
            NpcRuntimeWorldReadiness readiness = enabled() ? refreshWorldReadiness() : currentWorldReadiness();
            if (enabled() && !readiness.ready()) {
                NpcRuntimeResult failed = NpcRuntimeResult.failed(
                        request.requestId(),
                        "harness-world-not-ready",
                        request.ticks(),
                        "world",
                        readiness.displayReason(),
                        List.of()
                );
                writeResult(failed.requestId(), failed);
                rememberResult(failed);
                queue.archive(active);
                writeStatusBestEffort();
                return new ProcessOutcome(true, failed.requestId());
            }
            NpcRuntimeResult result;
            try {
                result = runner.run(request);
            } catch (TimeoutException exception) {
                result = NpcRuntimeResult.failed(
                        request.requestId(),
                        "harness-timeout",
                        request.ticks(),
                        "run",
                        exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName(),
                        List.of()
                );
            } catch (Exception exception) {
                result = NpcRuntimeResult.failed(
                        request.requestId(),
                        "runtime-error",
                        request.ticks(),
                        "run",
                        exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName(),
                        List.of()
                );
            }
            writeResult(result.requestId(), result);
            rememberResult(result);
            writeStatusBestEffort();
            if ("passed".equals(result.status())) {
                ensureTraceExists(request, result);
            }
            queue.archive(active);
            writeStatusBestEffort();
            return new ProcessOutcome(true, result.requestId());
        } catch (NpcRuntimeRequest.ValidationException exception) {
            String resultRequestId = exception.requestId().equals("<unknown>") ? active.requestId() : exception.requestId();
            NpcRuntimeResult failed = NpcRuntimeResult.failed(
                    resultRequestId,
                    exception.classification(),
                    0,
                    "parse",
                    exception.getMessage(),
                    exception.unsupported()
            );
            writeResult(resultRequestId, failed);
            rememberResult(failed);
            queue.archive(active);
            writeStatusBestEffort();
            return new ProcessOutcome(true, resultRequestId);
        } catch (Exception exception) {
            NpcRuntimeResult failed = NpcRuntimeResult.failed(
                    active.requestId(),
                    "invalid-request",
                    0,
                    "parse",
                    exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName(),
                    List.of()
            );
            writeResult(active.requestId(), failed);
            rememberResult(failed);
            queue.archive(active);
            writeStatusBestEffort();
            return new ProcessOutcome(true, active.requestId());
        } finally {
            activeRequestId = null;
            writeStatusBestEffort();
        }
    }

    @Nonnull
    public CancelOutcome cancel() throws IOException {
        Optional<NpcRuntimeRequestQueue.ActiveRequest> claimed = queue.claimNext();
        if (claimed.isEmpty()) {
            String active = activeRequestId;
            return new CancelOutcome(
                    false,
                    active,
                    active != null
                            ? "active request cancellation is not available for the current synchronous runner"
                            : "no queued request to cancel"
            );
        }

        NpcRuntimeRequestQueue.ActiveRequest active = claimed.get();
        NpcRuntimeResult canceled = NpcRuntimeResult.canceled(
                active.requestId(),
                requestedTicksOrZero(active.path()),
                "canceled through /npcruntime cancel"
        );
        writeResult(active.requestId(), canceled);
        rememberResult(canceled);
        queue.archive(active);
        writeStatusBestEffort();
        return new CancelOutcome(true, active.requestId(), "canceled queued request");
    }

    private void writeResult(@Nonnull String requestId, @Nonnull NpcRuntimeResult result) throws IOException {
        Path resultPath = config.paths().results().resolve(requestId + ".result.json");
        Files.createDirectories(resultPath.getParent());
        Files.writeString(resultPath, result.toJson(), StandardCharsets.UTF_8);
    }

    private void recoverStaleActiveRequests(@Nonnull String trigger) throws IOException {
        List<NpcRuntimeRequestQueue.ActiveRequest> activeRequests = queue.activeRequests();
        if (activeRequests.isEmpty()) {
            return;
        }

        ArrayList<NpcRuntimeRecoveryReport.RecoveredRequest> recovered = new ArrayList<>();
        for (NpcRuntimeRequestQueue.ActiveRequest active : activeRequests) {
            String message = "Recovered stale active request during " + trigger;
            NpcRuntimeResult result = NpcRuntimeResult.recoveredStaleActive(
                    active.requestId(),
                    requestedTicksOrZero(active.path()),
                    trigger,
                    message
            );
            Path resultPath = config.paths().results().resolve(active.requestId() + ".result.json");
            Path archivePath = queue.archivePath(active);
            writeResult(active.requestId(), result);
            rememberResult(result);
            queue.archive(active);
            recovered.add(NpcRuntimeRecoveryReport.RecoveredRequest.of(
                    active.requestId(),
                    result.classification(),
                    active.path(),
                    resultPath,
                    archivePath,
                    message
            ));
        }
        lastRecoveryReport = NpcRuntimeRecoveryReport.of(trigger, recovered);
    }

    private void recoverStaleActiveRequestsBestEffort(@Nonnull String trigger) {
        try {
            recoverStaleActiveRequests(trigger);
        } catch (Exception exception) {
            rememberStatusWriteFailure(exception);
        }
    }

    private int requestedTicksOrZero(@Nonnull Path requestPath) {
        try {
            String text = Files.readString(requestPath, StandardCharsets.UTF_8);
            return NpcRuntimeRequest.parse(text, config).ticks();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void rememberResult(@Nonnull NpcRuntimeResult result) {
        lastResultId = result.requestId();
        lastResultStatus = result.status();
        lastResultClassification = result.classification();
    }

    @Nonnull
    public NpcRuntimeHarnessStatus currentStatus() {
        long queued;
        try {
            queued = queuedCount();
        } catch (IOException ignored) {
            queued = -1;
        }
        NpcRuntimeHarnessStatus.LastResult lastResult = lastResultId != null
                ? new NpcRuntimeHarnessStatus.LastResult(
                        lastResultId,
                        lastResultStatus != null ? lastResultStatus : "unknown",
                        lastResultClassification != null ? lastResultClassification : "unknown"
                )
                : null;
        return NpcRuntimeHarnessStatus.fromService(
                config,
                enabled(),
                activeRequestId,
                queued,
                lastResult,
                lastRecoveryReport,
                currentWorldReadiness(),
                Instant.now()
        );
    }

    public void writeStatus() throws IOException {
        statusWriter.write(currentStatus());
        lastStatusWriteSucceeded = true;
        lastStatusWriteError = null;
    }

    @Nonnull
    public Path statusFile() {
        return statusWriter.statusFile();
    }

    public boolean lastStatusWriteSucceeded() {
        return lastStatusWriteSucceeded;
    }

    @Nullable
    public String lastStatusWriteError() {
        return lastStatusWriteError;
    }

    private void pollSafely() {
        try {
            if (enabled()) {
                NpcRuntimeWorldReadiness readiness = prepareRuntimeWorldBestEffort();
                if (readiness.ready() || readiness.shouldFailQueuedRequests()) {
                    processNextQueuedRequest();
                } else {
                    writeStatus();
                }
            } else {
                writeStatus();
            }
        } catch (Exception exception) {
            rememberStatusWriteFailure(exception);
        }
    }

    private void writeStatusBestEffort() {
        try {
            writeStatus();
        } catch (Exception exception) {
            rememberStatusWriteFailure(exception);
        }
    }

    private void rememberStatusWriteFailure(@Nonnull Exception exception) {
        lastStatusWriteSucceeded = false;
        lastStatusWriteError = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    @Nonnull
    private String statusWriteText() {
        return lastStatusWriteSucceeded
                ? "ok"
                : "failed:" + (lastStatusWriteError != null ? lastStatusWriteError : "not-written");
    }

    @Nonnull
    private NpcRuntimeWorldReadiness prepareRuntimeWorldBestEffort() {
        try {
            NpcRuntimeWorldReadiness readiness = worldReadinessPreparer.get();
            lastWorldReadiness = readiness;
            writeStatusBestEffort();
            return readiness;
        } catch (Throwable exception) {
            NpcRuntimeWorldReadiness readiness = NpcRuntimeWorldReadiness.notReady(
                    config.defaultWorldId(),
                    NpcRuntimeWorldReadiness.WORLD_LOAD_FAILED,
                    exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName()
            );
            lastWorldReadiness = readiness;
            writeStatusBestEffort();
            return readiness;
        }
    }

    @Nonnull
    private NpcRuntimeWorldReadiness currentWorldReadiness() {
        try {
            NpcRuntimeWorldReadiness readiness = worldReadinessSupplier.get();
            lastWorldReadiness = readiness;
            return readiness;
        } catch (Throwable exception) {
            NpcRuntimeWorldReadiness readiness = NpcRuntimeWorldReadiness.notReady(
                    config.defaultWorldId(),
                    NpcRuntimeWorldReadiness.WORLD_LOAD_FAILED,
                    exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName()
            );
            lastWorldReadiness = readiness;
            return readiness;
        }
    }

    @Nonnull
    private NpcRuntimeWorldReadiness refreshWorldReadiness() {
        return currentWorldReadiness();
    }

    private void ensureTraceExists(@Nonnull NpcRuntimeRequest request, @Nonnull NpcRuntimeResult result) throws IOException {
        Path tracePath = config.paths().traces().resolve(request.requestId() + ".trace.jsonl");
        if (Files.exists(tracePath)) {
            return;
        }
        try (NpcRuntimeTraceWriter writer = NpcRuntimeTraceWriter.open(tracePath)) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "run-start")
                    .with("assetId", request.assetId())
                    .with("roleId", request.roleId()));
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), result.ticksRun(), "run-end")
                    .with("status", result.status()));
        }
    }

    @Nonnull
    private static NpcRuntimeResult defaultDryRun(@Nonnull NpcRuntimeRequest request) {
        Path tracePath = Path.of(request.requestId() + ".trace.jsonl");
        return NpcRuntimeResult.passed(request, 0, tracePath, NpcRuntimeResult.Summary.empty());
    }

    @FunctionalInterface
    public interface ScenarioRunner {
        @Nonnull
        NpcRuntimeResult run(@Nonnull NpcRuntimeRequest request) throws Exception;
    }

    public record ProcessOutcome(boolean processed, @Nullable String requestId) {
    }

    public record CancelOutcome(boolean canceled, @Nullable String requestId, @Nonnull String message) {
    }
}
