package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeCleanupReportTest {
    @Test
    void serializesSuccessfulCleanupCounts() {
        NpcRuntimeCleanupReport report = NpcRuntimeCleanupReport.builder()
                .entityRemoval(true)
                .blockReset(true)
                .build();

        Map<String, Object> json = report.toMap();

        assertTrue(report.succeeded());
        assertEquals("removed entities=1, reset blocks=1", report.message());
        assertEquals(1, json.get("entityRemovalAttempted"));
        assertEquals(1, json.get("entityRemovalSucceeded"));
        assertEquals(0, json.get("entityRemovalFailed"));
        assertEquals(1, json.get("blockResetAttempted"));
        assertEquals(1, json.get("blockResetSucceeded"));
        assertEquals(true, json.get("succeeded"));
    }

    @Test
    void serializesFailedCleanupWithUnresolvedFixtures() {
        NpcRuntimeCleanupReport report = NpcRuntimeCleanupReport.builder()
                .entityRemoval(false)
                .unresolvedFixture("npcUnderTest")
                .build();

        Map<String, Object> json = report.toMap();

        assertFalse(report.succeeded());
        assertTrue(report.message().contains("cleanup failed"));
        assertEquals(1, json.get("entityRemovalFailed"));
        assertEquals(List.of("npcUnderTest"), json.get("unresolvedFixtureIds"));
    }
}
