package com.alechilles.alecsnpcdebuginspector.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.annotation.Nonnull;

/**
 * Writes the headless harness heartbeat atomically for external tools.
 */
public final class NpcRuntimeHarnessStatusWriter {
    private final Path statusFile;

    public NpcRuntimeHarnessStatusWriter(@Nonnull NpcRuntimePaths paths) {
        this(paths.statusFile());
    }

    public NpcRuntimeHarnessStatusWriter(@Nonnull Path statusFile) {
        this.statusFile = statusFile;
    }

    @Nonnull
    public Path statusFile() {
        return statusFile;
    }

    public void write(@Nonnull NpcRuntimeHarnessStatus status) throws IOException {
        Files.createDirectories(statusFile.getParent());
        Path tempFile = statusFile.resolveSibling(statusFile.getFileName() + ".tmp");
        Files.writeString(tempFile, status.toJson(), StandardCharsets.UTF_8);
        move(tempFile, statusFile);
    }

    private static void move(@Nonnull Path source, @Nonnull Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
