package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Machine-readable runtime harness result.
 */
public record NpcRuntimeResult(
        @Nonnull String status,
        @Nonnull String classification,
        @Nonnull String requestId,
        int ticksRequested,
        int ticksRun,
        @Nullable String tracePath,
        @Nullable ErrorInfo error,
        @Nonnull Instant startedAt,
        @Nonnull Instant endedAt,
        @Nonnull List<NpcRuntimeRequest.UnsupportedField> unsupported,
        @Nonnull Cleanup cleanup,
        @Nonnull Artifacts artifacts,
        @Nonnull Summary summary
) {
    @Nonnull
    public static NpcRuntimeResult passed(@Nonnull NpcRuntimeRequest request,
                                          int ticksRun,
                                          @Nonnull Path tracePath,
                                          @Nonnull Summary summary) {
        return passed(request, ticksRun, tracePath, summary, Cleanup.succeeded("completed"));
    }

    @Nonnull
    public static NpcRuntimeResult passed(@Nonnull NpcRuntimeRequest request,
                                          int ticksRun,
                                          @Nonnull Path tracePath,
                                          @Nonnull Summary summary,
                                          @Nonnull Cleanup cleanup) {
        Instant now = Instant.now();
        return new NpcRuntimeResult(
                "passed",
                "passed",
                request.requestId(),
                request.ticks(),
                ticksRun,
                tracePath.toString(),
                null,
                now,
                now,
                List.of(),
                cleanup,
                Artifacts.withTrace(tracePath),
                summary
        );
    }

    @Nonnull
    public static NpcRuntimeResult cleanupFailed(@Nonnull NpcRuntimeRequest request,
                                                 int ticksRun,
                                                 @Nonnull Path tracePath,
                                                 @Nonnull Summary summary,
                                                 @Nonnull NpcRuntimeCleanupReport cleanupReport) {
        Instant now = Instant.now();
        return new NpcRuntimeResult(
                "failed",
                "cleanup-failed",
                request.requestId(),
                request.ticks(),
                ticksRun,
                tracePath.toString(),
                new ErrorInfo("cleanup", cleanupReport.message(), null),
                now,
                now,
                List.of(),
                Cleanup.fromReport(cleanupReport),
                Artifacts.withTrace(tracePath),
                summary
        );
    }

    @Nonnull
    public static NpcRuntimeResult assertionFailed(@Nonnull NpcRuntimeRequest request,
                                                   int ticksRun,
                                                   @Nonnull Path tracePath,
                                                   @Nonnull Summary summary,
                                                   @Nonnull Cleanup cleanup,
                                                   @Nonnull String classification,
                                                   @Nonnull String message) {
        Instant now = Instant.now();
        return new NpcRuntimeResult(
                "failed",
                classification,
                request.requestId(),
                request.ticks(),
                ticksRun,
                tracePath.toString(),
                new ErrorInfo("assertions", message, null),
                now,
                now,
                List.of(),
                cleanup,
                Artifacts.withTrace(tracePath),
                summary
        );
    }

    @Nonnull
    public static NpcRuntimeResult failed(@Nonnull String requestId, @Nonnull String error) {
        return failed(requestId, "runtime-error", 0, "run", error, List.of());
    }

    @Nonnull
    public static NpcRuntimeResult failed(@Nonnull String requestId,
                                          @Nonnull String classification,
                                          int ticksRequested,
                                          @Nonnull String phase,
                                          @Nonnull String error,
                                          @Nonnull List<NpcRuntimeRequest.UnsupportedField> unsupported) {
        Instant now = Instant.now();
        return new NpcRuntimeResult(
                "failed",
                classification,
                requestId,
                ticksRequested,
                0,
                null,
                new ErrorInfo(phase, error, null),
                now,
                now,
                List.copyOf(unsupported),
                Cleanup.notStarted(),
                Artifacts.empty(),
                Summary.empty()
        );
    }

    @Nonnull
    public static NpcRuntimeResult recoveredStaleActive(@Nonnull String requestId,
                                                        int ticksRequested,
                                                        @Nonnull String trigger,
                                                        @Nonnull String message) {
        Instant now = Instant.now();
        return new NpcRuntimeResult(
                "failed",
                "harness-recovered-stale-active-request",
                requestId,
                ticksRequested,
                0,
                null,
                new ErrorInfo("recovery", message, trigger),
                now,
                now,
                List.of(),
                new Cleanup(true, true, "archived stale active request during " + trigger, null),
                Artifacts.empty(),
                Summary.empty()
        );
    }

    @Nonnull
    public static NpcRuntimeResult canceled(@Nonnull String requestId, int ticksRequested, @Nonnull String reason) {
        return canceled(requestId, ticksRequested, 0, reason, Cleanup.notStarted());
    }

    @Nonnull
    public static NpcRuntimeResult canceled(@Nonnull String requestId,
                                            int ticksRequested,
                                            int ticksRun,
                                            @Nonnull String reason,
                                            @Nonnull Cleanup cleanup) {
        Instant now = Instant.now();
        return new NpcRuntimeResult(
                "canceled",
                "user-canceled",
                requestId,
                ticksRequested,
                ticksRun,
                null,
                new ErrorInfo("cancel", reason, null),
                now,
                now,
                List.of(),
                cleanup,
                Artifacts.empty(),
                Summary.empty()
        );
    }

    @Nonnull
    public String toJson() {
        return NpcRuntimeJson.stringify(toMap());
    }

    @Nonnull
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("classification", classification);
        map.put("requestId", requestId);
        map.put("ticksRequested", ticksRequested);
        map.put("ticksRun", ticksRun);
        map.put("tracePath", tracePath);
        map.put("error", error != null ? error.toMap() : null);
        map.put("startedAt", startedAt.toString());
        map.put("endedAt", endedAt.toString());
        map.put("unsupported", unsupported.stream().map(NpcRuntimeRequest.UnsupportedField::toMap).toList());
        map.put("cleanup", cleanup.toMap());
        map.put("artifacts", artifacts.toMap());
        map.put("summary", summary.toMap());
        return map;
    }

    public record ErrorInfo(@Nonnull String phase, @Nonnull String message, @Nullable String type) {
        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("phase", phase);
            map.put("message", message);
            if (type != null) {
                map.put("type", type);
            }
            return map;
        }
    }

    public record Cleanup(boolean attempted,
                          boolean succeeded,
                          @Nonnull String message,
                          @Nullable NpcRuntimeCleanupReport report) {
        @Nonnull
        static Cleanup succeeded(@Nonnull String message) {
            return new Cleanup(true, true, message, null);
        }

        @Nonnull
        static Cleanup notStarted() {
            return new Cleanup(false, true, "not started", null);
        }

        @Nonnull
        static Cleanup fromReport(@Nonnull NpcRuntimeCleanupReport report) {
            return new Cleanup(true, report.succeeded(), report.message(), report);
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("attempted", attempted);
            map.put("succeeded", succeeded);
            map.put("message", message);
            if (report != null) {
                map.put("report", report.toMap());
            }
            return map;
        }
    }

    public record Artifacts(@Nullable String resultPath, @Nullable String tracePath) {
        @Nonnull
        static Artifacts empty() {
            return new Artifacts(null, null);
        }

        @Nonnull
        static Artifacts withTrace(@Nonnull Path tracePath) {
            return new Artifacts(null, tracePath.toString());
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            if (resultPath != null) {
                map.put("resultPath", resultPath);
            }
            if (tracePath != null) {
                map.put("tracePath", tracePath);
            }
            return map;
        }
    }

    public static final class Summary {
        private final List<String> statesSeen = new ArrayList<>();
        private final List<String> actionsInferred = new ArrayList<>();
        private final List<String> combatAbilitiesSeen = new ArrayList<>();
        private final List<String> timersSeen = new ArrayList<>();
        private final List<String> alarmsSeen = new ArrayList<>();
        private final List<NpcRuntimeAssertionResult> assertionResults = new ArrayList<>();

        private Summary() {
        }

        @Nonnull
        public static Summary empty() {
            return new Summary();
        }

        @Nonnull
        public Summary withState(@Nonnull String state) {
            addUnique(statesSeen, state);
            return this;
        }

        @Nonnull
        public Summary withAssertions(@Nonnull List<NpcRuntimeAssertionResult> results) {
            assertionResults.clear();
            assertionResults.addAll(results);
            return this;
        }

        public boolean hasAssertionFailures() {
            return assertionResults.stream().anyMatch(result -> "failed".equals(result.status()));
        }

        public boolean hasAssertionUnknowns() {
            return assertionResults.stream().anyMatch(result -> "unknown".equals(result.status()));
        }

        @Nonnull
        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("statesSeen", List.copyOf(statesSeen));
            map.put("actionsInferred", List.copyOf(actionsInferred));
            map.put("combatAbilitiesSeen", List.copyOf(combatAbilitiesSeen));
            map.put("timersSeen", List.copyOf(timersSeen));
            map.put("alarmsSeen", List.copyOf(alarmsSeen));
            map.put("assertions", assertionResults.stream().map(NpcRuntimeAssertionResult::toMap).toList());
            return map;
        }

        private static void addUnique(@Nonnull List<String> values, @Nonnull String value) {
            if (!values.contains(value)) {
                values.add(value);
            }
        }
    }
}
