package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Machine-readable readiness state for the dedicated NPC runtime world.
 */
public record NpcRuntimeWorldReadiness(
        boolean ready,
        @Nonnull String worldId,
        boolean ticking,
        boolean paused,
        int playerCount,
        @Nonnull String reason,
        @Nullable String detail
) {
    public static final String READY = "ready";
    public static final String UNIVERSE_NOT_AVAILABLE = "universe-not-available";
    public static final String UNIVERSE_NOT_READY = "universe-not-ready";
    public static final String WORLD_NOT_LOADED = "world-not-loaded";
    public static final String WORLD_LOAD_FAILED = "world-load-failed";
    public static final String WORLD_NOT_TICKING = "world-not-ticking";
    public static final String WORLD_PAUSED = "world-paused";

    @Nonnull
    public static NpcRuntimeWorldReadiness ready(@Nonnull String worldId, int playerCount) {
        return new NpcRuntimeWorldReadiness(true, worldId, true, false, playerCount, READY, null);
    }

    @Nonnull
    public static NpcRuntimeWorldReadiness notReady(@Nonnull String worldId,
                                                   @Nonnull String reason,
                                                   @Nullable String detail) {
        return new NpcRuntimeWorldReadiness(false, worldId, false, false, 0, reason, detail);
    }

    @Nonnull
    public static NpcRuntimeWorldReadiness fromWorld(@Nonnull String expectedWorldId,
                                                    @Nonnull String actualWorldId,
                                                    boolean ticking,
                                                    boolean paused,
                                                    int playerCount) {
        if (!ticking) {
            return new NpcRuntimeWorldReadiness(
                    false,
                    actualWorldId,
                    false,
                    paused,
                    playerCount,
                    WORLD_NOT_TICKING,
                    "World " + expectedWorldId + " is loaded but not ticking"
            );
        }
        if (paused) {
            return new NpcRuntimeWorldReadiness(
                    false,
                    actualWorldId,
                    true,
                    true,
                    playerCount,
                    WORLD_PAUSED,
                    "World " + expectedWorldId + " is loaded but paused"
            );
        }
        return ready(actualWorldId, playerCount);
    }

    public boolean shouldFailQueuedRequests() {
        return !ready
                && !UNIVERSE_NOT_AVAILABLE.equals(reason)
                && !UNIVERSE_NOT_READY.equals(reason);
    }

    @Nonnull
    public String displayReason() {
        return detail != null && !detail.isBlank() ? reason + ": " + detail : reason;
    }

    @Nonnull
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("ready", ready);
        map.put("worldId", worldId);
        map.put("ticking", ticking);
        map.put("paused", paused);
        map.put("playerCount", playerCount);
        map.put("reason", reason);
        map.put("detail", detail);
        return map;
    }
}
