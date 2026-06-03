package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeTraceWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesJsonlTraceRecords() throws Exception {
        Path trace = tempDir.resolve("run.trace.jsonl");

        try (NpcRuntimeTraceWriter writer = NpcRuntimeTraceWriter.open(trace)) {
            writer.write(NpcRuntimeTraceRecord.of("request-a", 0, "run-start")
                    .with("assetId", "Boar")
                    .with("position", List.of(0, 64, 0)));
            writer.write(NpcRuntimeTraceRecord.of("request-a", 5, "run-end")
                    .with("status", "passed"));
        }

        List<String> lines = Files.readAllLines(trace);
        assertEquals(2, lines.size());
        assertTrue(lines.getFirst().contains("\"kind\":\"run-start\""));
        assertTrue(lines.getFirst().contains("\"position\":[0,64,0]"));
        assertTrue(lines.getLast().contains("\"status\":\"passed\""));
    }

    @Test
    void refusesToExceedTraceByteLimit() throws Exception {
        Path trace = tempDir.resolve("limited.trace.jsonl");

        try (NpcRuntimeTraceWriter writer = NpcRuntimeTraceWriter.open(trace, 80)) {
            writer.write(NpcRuntimeTraceRecord.of("request-a", 0, "run-start"));

            Assertions.assertThrows(java.io.IOException.class, () -> writer.write(
                    NpcRuntimeTraceRecord.of("request-a", 1, "npc-snapshot")
                            .with("details", "this record is intentionally too large for the test limit")
            ));
        }
    }
}
