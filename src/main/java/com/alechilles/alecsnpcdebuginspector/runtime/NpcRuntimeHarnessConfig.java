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
}
