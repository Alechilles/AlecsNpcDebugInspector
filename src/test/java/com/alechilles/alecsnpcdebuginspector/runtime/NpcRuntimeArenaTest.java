package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeArenaTest {
    @Test
    void arenaResetTraceRecordsIncludeFixtureSetupPhase() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "arena_phase_trace",
                  "assetId": "Asset",
                  "roleId": "Role",
                  "ticks": 5,
                  "world": {"arena": "fixture-lab"},
                  "fixtures": {
                    "list": [
                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "Role"},
                      {"fixtureId": "target.Enemy", "kind": "targetDummy", "roleId": "Role", "targetSlot": "Enemy"}
                    ]
                  }
                }
                """,
                config
        );
        NpcRuntimeArena arena = NpcRuntimeArena.defaultArena("npc_runtime_test_flatworld", "fixture-lab", 12345L);

        NpcRuntimeTraceRecord before = NpcRuntimeLiveScenarioRunner.arenaResetRecord(
                request,
                0,
                arena,
                "before-fixture-setup"
        );
        NpcRuntimeTraceRecord after = NpcRuntimeLiveScenarioRunner.arenaResetRecord(
                request,
                0,
                arena,
                "after-fixture-setup"
        );

        assertEquals("arena-reset", before.fields().get("kind"));
        assertEquals("fixture-lab", before.fields().get("arena"));
        assertEquals("before-fixture-setup", before.fields().get("phase"));
        assertEquals("after-fixture-setup", after.fields().get("phase"));
        assertEquals(2, before.fields().get("fixtureCount"));
        assertTrue(before.fields().get("details").toString().contains("blockResetMode=fixture-block-mutations-reset-during-cleanup"));
    }

    @Test
    void environmentSetupTraceRecordsRequestedWorldControls() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "environment_trace",
                  "assetId": "Asset",
                  "roleId": "Role",
                  "ticks": 5,
                  "environment": {
                    "timeOfDay": 6000,
                    "weather": "hytale:clear",
                    "pauseTime": true,
                    "light": 15
                  }
                }
                """,
                config
        );

        NpcRuntimeTraceRecord record = NpcRuntimeLiveScenarioRunner.environmentSetupRecord(request, 0);

        assertEquals("environment-setup", record.fields().get("kind"));
        assertEquals(6000, record.fields().get("timeOfDay"));
        assertEquals("hytale:clear", record.fields().get("weather"));
        assertEquals(true, record.fields().get("pauseTime"));
        assertEquals(15, record.fields().get("light"));
        assertEquals(false, record.fields().get("engineApplied"));
        assertTrue(record.fields().get("unsupportedFields").toString().contains("engineTimeMutation"));
    }

    @Test
    void targetInductionTraceRecordsNaturalSensorModeWhenTargetFixturesExist() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "target_induction_available",
                  "assetId": "Asset",
                  "roleId": "Role",
                  "ticks": 120,
                  "fixtures": {
                    "list": [
                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "Role"},
                      {"fixtureId": "target.Enemy", "kind": "targetDummy", "roleId": "Role", "targetSlot": "Enemy"}
                    ]
                  }
                }
                """,
                config
        );

        NpcRuntimeTraceRecord record = NpcRuntimeLiveScenarioRunner.targetInductionRecord(request, 0);

        assertEquals("target-induction", record.fields().get("kind"));
        assertEquals("natural-sensor", record.fields().get("inductionMode"));
        assertEquals("available", record.fields().get("status"));
        assertEquals(false, record.fields().get("preseeded"));
        assertTrue(record.fields().get("targetFixtureIds").toString().contains("target.Enemy"));
        assertTrue(record.fields().get("targetSlots").toString().contains("Enemy"));
        assertTrue(record.fields().get("unsupportedFields").toString().contains("preseedTargetSlots"));
    }

    @Test
    void targetInductionTraceRecordsUnavailableWhenNoTargetFixtureExists() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "target_induction_unavailable",
                  "assetId": "Asset",
                  "roleId": "Role",
                  "ticks": 120
                }
                """,
                config
        );

        NpcRuntimeTraceRecord record = NpcRuntimeLiveScenarioRunner.targetInductionRecord(request, 0);

        assertEquals("target-induction", record.fields().get("kind"));
        assertEquals("natural-sensor", record.fields().get("inductionMode"));
        assertEquals("unavailable", record.fields().get("status"));
        assertEquals(false, record.fields().get("preseeded"));
        assertEquals("[]", record.fields().get("targetFixtureIds").toString());
        assertEquals("[]", record.fields().get("targetSlots").toString());
    }
}
