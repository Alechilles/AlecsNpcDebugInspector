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
        assertTrue(before.fields().get("details").toString().contains("blockResetMode=no-block-mutations-yet"));
    }
}
