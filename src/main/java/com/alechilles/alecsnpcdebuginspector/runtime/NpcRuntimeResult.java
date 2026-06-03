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
        @Nonnull String requestId,
        int ticksRun,
        @Nullable String tracePath,
        @Nullable String error,
        @Nonnull Instant startedAt,
        @Nonnull Instant endedAt,
        boolean cleanupSucceeded,
        @Nonnull Summary summary
) {
    @Nonnull
    public static NpcRuntimeResult passed(@Nonnull NpcRuntimeRequest request,
                                          int ticksRun,
                                          @Nonnull Path tracePath,
                                          @Nonnull Summary summary) {
        Instant now = Instant.now();
        return new NpcRuntimeResult(
                "passed",
                request.requestId(),
                ticksRun,
                tracePath.toString(),
                null,
                now,
                now,
                true,
                summary
        );
    }

    @Nonnull
    public static NpcRuntimeResult failed(@Nonnull String requestId, @Nonnull String error) {
        Instant now = Instant.now();
        return new NpcRuntimeResult("failed", requestId, 0, null, error, now, now, true, Summary.empty());
    }

    @Nonnull
    public String toJson() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("requestId", requestId);
        map.put("ticksRun", ticksRun);
        if (tracePath != null) {
            map.put("tracePath", tracePath);
        }
        if (error != null) {
            map.put("error", error);
        }
        map.put("startedAt", startedAt.toString());
        map.put("endedAt", endedAt.toString());
        map.put("cleanupSucceeded", cleanupSucceeded);
        map.put("summary", summary.toMap());
        return NpcRuntimeJson.stringify(map);
    }

    public static final class Summary {
        private final List<String> statesSeen = new ArrayList<>();
        private final List<String> actionsInferred = new ArrayList<>();
        private final List<String> combatAbilitiesSeen = new ArrayList<>();
        private final List<String> timersSeen = new ArrayList<>();
        private final List<String> alarmsSeen = new ArrayList<>();

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
        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("statesSeen", List.copyOf(statesSeen));
            map.put("actionsInferred", List.copyOf(actionsInferred));
            map.put("combatAbilitiesSeen", List.copyOf(combatAbilitiesSeen));
            map.put("timersSeen", List.copyOf(timersSeen));
            map.put("alarmsSeen", List.copyOf(alarmsSeen));
            return map;
        }

        private static void addUnique(@Nonnull List<String> values, @Nonnull String value) {
            if (!values.contains(value)) {
                values.add(value);
            }
        }
    }
}
