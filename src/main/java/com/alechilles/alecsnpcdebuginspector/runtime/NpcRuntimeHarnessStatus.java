package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Machine-readable heartbeat for headless runtime harness orchestration.
 */
public record NpcRuntimeHarnessStatus(
        int version,
        boolean serverReady,
        boolean harnessEnabled,
        boolean worldReady,
        @Nonnull String worldId,
        boolean worldTicking,
        boolean worldPaused,
        @Nonnull String worldReadyReason,
        @Nullable String worldReadyDetail,
        int playerCount,
        @Nullable String activeRequestId,
        long queuedCount,
        @Nullable LastResult lastResult,
        @Nonnull Paths paths,
        @Nonnull Instant updatedAt
) {
    @Nonnull
    public static NpcRuntimeHarnessStatus fromService(@Nonnull NpcRuntimeHarnessConfig config,
                                                      boolean harnessEnabled,
                                                      @Nullable String activeRequestId,
                                                      long queuedCount,
                                                      @Nullable LastResult lastResult,
                                                      @Nonnull NpcRuntimeWorldReadiness worldReadiness,
                                                      @Nonnull Instant updatedAt) {
        return new NpcRuntimeHarnessStatus(
                1,
                true,
                harnessEnabled,
                worldReadiness.ready(),
                worldReadiness.worldId(),
                worldReadiness.ticking(),
                worldReadiness.paused(),
                worldReadiness.reason(),
                worldReadiness.detail(),
                worldReadiness.playerCount(),
                activeRequestId,
                queuedCount,
                lastResult,
                Paths.from(config.paths()),
                updatedAt
        );
    }

    @Nonnull
    public String toJson() {
        return NpcRuntimeJson.stringify(toMap());
    }

    @Nonnull
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("version", version);
        map.put("serverReady", serverReady);
        map.put("harnessEnabled", harnessEnabled);
        map.put("worldReady", worldReady);
        map.put("worldId", worldId);
        map.put("worldTicking", worldTicking);
        map.put("worldPaused", worldPaused);
        map.put("worldReadyReason", worldReadyReason);
        map.put("worldReadyDetail", worldReadyDetail);
        map.put("playerCount", playerCount);
        map.put("activeRequestId", activeRequestId);
        map.put("queuedCount", queuedCount);
        map.put("lastResult", lastResult != null ? lastResult.toMap() : null);
        map.put("paths", paths.toMap());
        map.put("updatedAt", updatedAt.toString());
        return map;
    }

    public record LastResult(@Nonnull String requestId, @Nonnull String status, @Nonnull String classification) {
        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("requestId", requestId);
            map.put("status", status);
            map.put("classification", classification);
            return map;
        }
    }

    public record Paths(@Nonnull String root,
                        @Nonnull String requests,
                        @Nonnull String results,
                        @Nonnull String traces,
                        @Nonnull String archive) {
        @Nonnull
        static Paths from(@Nonnull NpcRuntimePaths paths) {
            return new Paths(
                    normalize(paths.root()),
                    normalize(paths.requests()),
                    normalize(paths.results()),
                    normalize(paths.traces()),
                    normalize(paths.archive())
            );
        }

        @Nonnull
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("root", root);
            map.put("requests", requests);
            map.put("results", results);
            map.put("traces", traces);
            map.put("archive", archive);
            return map;
        }

        @Nonnull
        private static String normalize(@Nonnull Path path) {
            return path.toString();
        }
    }

}
