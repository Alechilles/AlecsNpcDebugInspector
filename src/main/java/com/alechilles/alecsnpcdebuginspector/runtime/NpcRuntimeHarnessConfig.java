package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import javax.annotation.Nonnull;

/**
 * Safety-focused configuration for the runtime harness.
 */
public record NpcRuntimeHarnessConfig(
        boolean enabledByDefault,
        boolean autoEnable,
        int maxTicks,
        int maxEntities,
        long maxTraceBytes,
        @Nonnull String instanceId,
        long pollIntervalMillis,
        long statusWriteIntervalMillis,
        long statusStaleAfterMillis,
        @Nonnull String defaultWorldId,
        @Nonnull NpcRuntimePaths paths
) {
    private static final String AUTO_ENABLE_PROPERTY = "alec.npcRuntime.autoEnable";
    private static final String AUTO_ENABLE_ENV = "ALEC_NPC_RUNTIME_AUTO_ENABLE";

    @Nonnull
    public static NpcRuntimeHarnessConfig developmentDefault(@Nonnull Path userDataRoot) {
        return new NpcRuntimeHarnessConfig(
                false,
                autoEnableFromEnvironment(),
                1200,
                64,
                8_388_608L,
                "npc_runtime_test_flatworld",
                1000,
                1000,
                5000,
                "npc_runtime_test_flatworld",
                NpcRuntimePaths.underUserData(userDataRoot)
        );
    }

    static boolean autoEnableFromEnvironment() {
        return isTruthy(System.getProperty(AUTO_ENABLE_PROPERTY))
                || isTruthy(System.getenv(AUTO_ENABLE_ENV));
    }

    private static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "true".equals(normalized)
                || "1".equals(normalized)
                || "yes".equals(normalized)
                || "on".equals(normalized);
    }

    public int clampTicks(int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be greater than zero");
        }
        if (ticks > maxTicks) {
            throw new IllegalArgumentException("ticks exceeds maxTicks " + maxTicks);
        }
        return ticks;
    }

    public int validateEntityCount(int entityCount) {
        if (entityCount < 0) {
            throw new IllegalArgumentException("entity count must not be negative");
        }
        if (entityCount > maxEntities) {
            throw new IllegalArgumentException("entity count exceeds maxEntities " + maxEntities);
        }
        return entityCount;
    }

    public long validateTraceBytes(long traceBytes) {
        if (traceBytes <= 0) {
            throw new IllegalArgumentException("maxTraceBytes must be greater than zero");
        }
        if (traceBytes > maxTraceBytes) {
            throw new IllegalArgumentException("maxTraceBytes exceeds configured limit " + maxTraceBytes);
        }
        return traceBytes;
    }

    public void validateWorldId(@Nonnull String worldId) {
        if (!instanceId.equals(worldId)) {
            throw new IllegalArgumentException("world.instanceId must be " + instanceId + " for the runtime harness");
        }
    }

    public boolean initiallyEnabled() {
        return enabledByDefault || autoEnable;
    }
}
