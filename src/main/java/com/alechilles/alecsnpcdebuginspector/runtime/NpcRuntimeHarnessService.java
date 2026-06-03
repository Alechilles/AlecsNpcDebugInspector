package com.alechilles.alecsnpcdebuginspector.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Coordinates request queue processing and the bounded scenario runner.
 */
public final class NpcRuntimeHarnessService {
    private final NpcRuntimeHarnessConfig config;
    private final NpcRuntimeRequestQueue queue;
    private final ScenarioRunner runner;
    private final AtomicBoolean enabled;
    private volatile String activeRequestId;
    private volatile String lastResultId;
    private volatile String lastResultStatus;

    public NpcRuntimeHarnessService(@Nonnull NpcRuntimeHarnessConfig config) {
        this(config, NpcRuntimeHarnessService::defaultDryRun);
    }

    public NpcRuntimeHarnessService(@Nonnull NpcRuntimeHarnessConfig config, @Nonnull ScenarioRunner runner) {
        this.config = config;
        this.queue = new NpcRuntimeRequestQueue(config.paths());
        this.runner = runner;
        this.enabled = new AtomicBoolean(config.enabledByDefault());
    }

    public void initializeDirectories() throws IOException {
        queue.ensureDirectories();
    }

    public boolean enabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
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
        String last = lastResultId != null ? lastResultId + ":" + lastResultStatus : "<none>";
        long queued;
        try {
            queued = queuedCount();
        } catch (IOException exception) {
                return "NPC Runtime Harness: enabled=" + enabled() + " active=" + active
                    + " queue=<error: " + exception.getMessage() + ">"
                    + " last=" + last
                    + " root=" + config.paths().root();
        }
        return "NPC Runtime Harness: enabled=" + enabled()
                + " active=" + active
                + " queued=" + queued
                + " last=" + last
                + " root=" + config.paths().root();
    }

    @Nonnull
    public String pathsText() {
        NpcRuntimePaths paths = config.paths();
        return "NPC Runtime Harness paths: root=" + paths.root()
                + " requests=" + paths.requests()
                + " results=" + paths.results()
                + " traces=" + paths.traces()
                + " archive=" + paths.archive();
    }

    @Nonnull
    public NpcRuntimeHarnessConfig config() {
        return config;
    }

    @Nonnull
    public ProcessOutcome processNextQueuedRequest() throws IOException {
        Optional<NpcRuntimeRequestQueue.ActiveRequest> claimed = queue.claimNext();
        if (claimed.isEmpty()) {
            return new ProcessOutcome(false, null);
        }

        NpcRuntimeRequestQueue.ActiveRequest active = claimed.get();
        activeRequestId = active.requestId();
        try {
            String text = Files.readString(active.path(), StandardCharsets.UTF_8);
            NpcRuntimeRequest request = NpcRuntimeRequest.parse(text, config);
            activeRequestId = request.requestId();
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
            if ("passed".equals(result.status())) {
                ensureTraceExists(request, result);
            }
            queue.archive(active);
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
            return new ProcessOutcome(true, active.requestId());
        } finally {
            activeRequestId = null;
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
        return new CancelOutcome(true, active.requestId(), "canceled queued request");
    }

    private void writeResult(@Nonnull String requestId, @Nonnull NpcRuntimeResult result) throws IOException {
        Path resultPath = config.paths().results().resolve(requestId + ".result.json");
        Files.createDirectories(resultPath.getParent());
        Files.writeString(resultPath, result.toJson(), StandardCharsets.UTF_8);
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
