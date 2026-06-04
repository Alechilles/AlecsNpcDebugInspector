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
        assertEquals(0, json.get("environmentRestoreAttempted"));
        assertEquals(0, json.get("environmentRestoreSucceeded"));
        assertEquals(0, json.get("environmentRestoreFailed"));
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

    @Test
    void serializesSkippedEnvironmentRestoreReason() {
        NpcRuntimeCleanupReport report = NpcRuntimeCleanupReport.builder()
                .environmentRestoreSkipped("environment setup was declarative-only; no engine mutation applied")
                .build();

        Map<String, Object> json = report.toMap();

        assertTrue(report.succeeded());
        assertEquals(0, json.get("environmentRestoreAttempted"));
        assertEquals(0, json.get("environmentRestoreSucceeded"));
        assertEquals(0, json.get("environmentRestoreFailed"));
        assertEquals("environment setup was declarative-only; no engine mutation applied", json.get("environmentRestoreSkippedReason"));
    }

    @Test
    void failedEnvironmentRestoreMakesCleanupFail() {
        NpcRuntimeCleanupReport report = NpcRuntimeCleanupReport.builder()
                .environmentRestore(false)
                .build();

        Map<String, Object> json = report.toMap();

        assertFalse(report.succeeded());
        assertTrue(report.message().contains("environmentFailures=1"));
        assertEquals(1, json.get("environmentRestoreAttempted"));
        assertEquals(1, json.get("environmentRestoreFailed"));
    }
}
