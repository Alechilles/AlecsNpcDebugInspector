package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import javax.annotation.Nonnull;

/**
 * Filesystem layout for file-driven NPC runtime harness automation.
 */
public record NpcRuntimePaths(
        @Nonnull Path root,
        @Nonnull Path requests,
        @Nonnull Path active,
        @Nonnull Path results,
        @Nonnull Path traces,
        @Nonnull Path archive
) {
    @Nonnull
    public static NpcRuntimePaths underUserData(@Nonnull Path userDataRoot) {
        Path root = userDataRoot.resolve("NpcRuntimeHarness");
        return new NpcRuntimePaths(
                root,
                root.resolve("requests"),
                root.resolve("active"),
                root.resolve("results"),
                root.resolve("traces"),
                root.resolve("archive")
        );
    }
}
