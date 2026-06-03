package com.alechilles.alecsnpcdebuginspector.runtime;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/**
 * Writes runtime traces as newline-delimited JSON.
 */
public final class NpcRuntimeTraceWriter implements Closeable {
    private final BufferedWriter writer;

    private NpcRuntimeTraceWriter(@Nonnull BufferedWriter writer) {
        this.writer = writer;
    }

    @Nonnull
    public static NpcRuntimeTraceWriter open(@Nonnull Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return new NpcRuntimeTraceWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
    }

    public void write(@Nonnull NpcRuntimeTraceRecord record) throws IOException {
        writer.write(record.toJson());
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
