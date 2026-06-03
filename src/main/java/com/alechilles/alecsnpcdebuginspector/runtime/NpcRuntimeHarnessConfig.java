package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import javax.annotation.Nonnull;

/**
 * Safety-focused configuration for the runtime harness.
 */
public record NpcRuntimeHarnessConfig(
        boolean enabledByDefault,
        int maxTicks,
        int maxEntities,
        long maxTraceBytes,
        @Nonnull String instanceId,
        long pollIntervalMillis,
        @Nonnull NpcRuntimePaths paths
) {
    @Nonnull
    public static NpcRuntimeHarnessConfig developmentDefault(@Nonnull Path userDataRoot) {
        return new NpcRuntimeHarnessConfig(
                false,
                1200,
                64,
                1_048_576L,
                "npc_runtime_test_flatworld",
                1000,
                NpcRuntimePaths.underUserData(userDataRoot)
        );
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
}
