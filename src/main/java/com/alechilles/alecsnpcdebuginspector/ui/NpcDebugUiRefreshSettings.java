package com.alechilles.alecsnpcdebuginspector.ui;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Shared per-player refresh interval settings for NPC debug UI surfaces.
 */
public final class NpcDebugUiRefreshSettings {
    public static final long DEFAULT_INTERVAL_MS = 1000L;
    public static final long MIN_INTERVAL_MS = 150L;
    public static final long MAX_INTERVAL_MS = 2000L;
    public static final long STEP_INTERVAL_MS = 50L;

    private static final Map<UUID, Long> INTERVALS_BY_PLAYER = new ConcurrentHashMap<>();

    private NpcDebugUiRefreshSettings() {
    }

    public static long getIntervalMs(@Nonnull PlayerRef playerRef) {
        return getIntervalMs(playerRef.getUuid());
    }

    public static long getIntervalMs(@Nonnull UUID playerUuid) {
        Long stored = INTERVALS_BY_PLAYER.get(playerUuid);
        if (stored == null) {
            return DEFAULT_INTERVAL_MS;
        }
        return normalize(stored);
    }

    public static long setIntervalMs(@Nonnull PlayerRef playerRef, long intervalMs) {
        return setIntervalMs(playerRef.getUuid(), intervalMs);
    }

    public static long setIntervalMs(@Nonnull UUID playerUuid, long intervalMs) {
        long normalized = normalize(intervalMs);
        INTERVALS_BY_PLAYER.put(playerUuid, normalized);
        return normalized;
    }

    public static long setFromUiValue(@Nonnull PlayerRef playerRef, double rawValue) {
        if (!Double.isFinite(rawValue)) {
            return getIntervalMs(playerRef);
        }
        return setIntervalMs(playerRef, Math.round(rawValue));
    }

    public static double toUiValue(long intervalMs) {
        return normalize(intervalMs);
    }

    @Nonnull
    public static String formatIntervalLabel(long intervalMs) {
        long normalized = normalize(intervalMs);
        return "Update Rate: " + normalized + " ms (" + String.format(java.util.Locale.ROOT, "%.2f", normalized / 1000.0d) + "s)";
    }

    private static long normalize(long raw) {
        long clamped = Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, raw));
        long offset = clamped - MIN_INTERVAL_MS;
        long steps = Math.round((double) offset / (double) STEP_INTERVAL_MS);
        long stepped = MIN_INTERVAL_MS + (steps * STEP_INTERVAL_MS);
        return Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, stepped));
    }
}
