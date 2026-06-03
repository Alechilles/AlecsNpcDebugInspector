package com.alechilles.alecsnpcdebuginspector.runtime;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * File queue for runtime request handoff.
 */
public final class NpcRuntimeRequestQueue {
    private final NpcRuntimePaths paths;

    public NpcRuntimeRequestQueue(@Nonnull NpcRuntimePaths paths) {
        this.paths = paths;
    }

    public void ensureDirectories() throws IOException {
        Files.createDirectories(paths.requests());
        Files.createDirectories(paths.active());
        Files.createDirectories(paths.results());
        Files.createDirectories(paths.traces());
        Files.createDirectories(paths.archive());
    }

    public long queuedCount() throws IOException {
        ensureDirectories();
        try (Stream<Path> stream = Files.list(paths.requests())) {
            return stream.filter(NpcRuntimeRequestQueue::isRequestFile).count();
        }
    }

    @Nonnull
    public Optional<ActiveRequest> claimNext() throws IOException {
        ensureDirectories();
        try (Stream<Path> stream = Files.list(paths.requests())) {
            Optional<Path> next = stream
                    .filter(NpcRuntimeRequestQueue::isRequestFile)
                    .sorted()
                    .findFirst();
            if (next.isEmpty()) {
                return Optional.empty();
            }
            Path source = next.get();
            Path active = paths.active().resolve(source.getFileName());
            move(source, active);
            return Optional.of(new ActiveRequest(requestIdFromFile(source), active));
        }
    }

    public void archive(@Nonnull ActiveRequest activeRequest) throws IOException {
        Path target = paths.archive().resolve(activeRequest.path().getFileName());
        move(activeRequest.path(), target);
    }

    private static boolean isRequestFile(@Nonnull Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".request.json");
    }

    @Nonnull
    static String requestIdFromFile(@Nonnull Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".request.json")) {
            return name.substring(0, name.length() - ".request.json".length());
        }
        return name;
    }

    private static void move(@Nonnull Path source, @Nonnull Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record ActiveRequest(@Nonnull String requestId, @Nonnull Path path) {
    }
}
