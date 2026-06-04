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
    private final long maxBytes;
    private long bytesWritten;

    private NpcRuntimeTraceWriter(@Nonnull BufferedWriter writer, long maxBytes) {
        this.writer = writer;
        this.maxBytes = maxBytes;
    }

    @Nonnull
    public static NpcRuntimeTraceWriter open(@Nonnull Path path) throws IOException {
        return open(path, Long.MAX_VALUE);
    }

    @Nonnull
    public static NpcRuntimeTraceWriter open(@Nonnull Path path, long maxBytes) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return new NpcRuntimeTraceWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8), maxBytes);
    }

    public void write(@Nonnull NpcRuntimeTraceRecord record) throws IOException {
        String json = record.toJson();
        long recordBytes = json.getBytes(StandardCharsets.UTF_8).length + System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
        if (bytesWritten + recordBytes > maxBytes) {
            throw new IOException("NPC runtime trace exceeded maxTraceBytes " + maxBytes);
        }
        writer.write(json);
        writer.newLine();
        writer.flush();
        bytesWritten += recordBytes;
    }

    public long bytesWritten() {
        return bytesWritten;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
