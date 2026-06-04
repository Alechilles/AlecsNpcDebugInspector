package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void refusesToOverwriteReservedTraceFields() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> NpcRuntimeTraceRecord.of("request-a", 0, "fixture-spawn").with("kind", "targetDummy")
        );

        assertTrue(exception.getMessage().contains("reserved"));
    }

    @Test
    void serializesRuntimeContractEvidenceHelpers() {
        String tameworkMutation = NpcRuntimeTraceRecord.tameworkFixtureMutation(
                "request-a",
                0,
                "npc_under_test",
                "tamed",
                true,
                true,
                "applied",
                List.of()
        ).toJson();
        String targetSelection = NpcRuntimeTraceRecord.targetSelectionEvidence(
                "request-a",
                4,
                "npc_under_test",
                "hostile_a",
                2,
                true,
                "nearest-hostile"
        ).toJson();
        String pathing = NpcRuntimeTraceRecord.pathingEvidence(
                "request-a",
                5,
                "npc_under_test",
                "hostile_a",
                List.of(4, 70, 3),
                "pathing",
                false
        ).toJson();
        String combatEligibility = NpcRuntimeTraceRecord.combatEligibilityEvidence(
                "request-a",
                6,
                "npc_under_test",
                "hostile_a",
                "MeleeAttack",
                true,
                true,
                true,
                true,
                "eligible"
        ).toJson();
        String lifecycle = NpcRuntimeTraceRecord.instructionLifecycleEvidence(
                "request-a",
                7,
                "npc_under_test",
                "MeleeAttack",
                "started",
                "candidate"
        ).toJson();

        assertTrue(tameworkMutation.contains("\"kind\":\"tamework-fixture-mutation\""));
        assertTrue(tameworkMutation.contains("\"fixtureId\":\"npc_under_test\""));
        assertTrue(tameworkMutation.contains("\"requestedValue\":true"));
        assertTrue(targetSelection.contains("\"kind\":\"target-selection-evidence\""));
        assertTrue(targetSelection.contains("\"candidateCount\":2"));
        assertTrue(pathing.contains("\"kind\":\"pathing-evidence\""));
        assertTrue(pathing.contains("\"destination\":[4,70,3]"));
        assertTrue(combatEligibility.contains("\"kind\":\"combat-eligibility-evidence\""));
        assertTrue(combatEligibility.contains("\"lineOfSightOk\":true"));
        assertTrue(lifecycle.contains("\"kind\":\"instruction-lifecycle-evidence\""));
        assertTrue(lifecycle.contains("\"previousStatus\":\"candidate\""));
    }
}
