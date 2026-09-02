package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeResultTest {
    @Test
    void cleanupFailedResultCarriesStructuredCleanupReport() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"cleanup_bad\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}",
                config
        );
        NpcRuntimeCleanupReport cleanupReport = NpcRuntimeCleanupReport.builder()
                .entityRemoval(false)
                .unresolvedFixture("npcUnderTest")
                .build();

        NpcRuntimeResult result = NpcRuntimeResult.cleanupFailed(
                request,
                5,
                Path.of("cleanup_bad.trace.jsonl"),
                NpcRuntimeResult.Summary.empty(),
                cleanupReport
        );

        Map<String, Object> json = NpcRuntimeJson.parseObject(result.toJson());
        assertEquals("failed", json.get("status"));
        assertEquals("cleanup-failed", json.get("classification"));
        assertTrue(json.get("error").toString().contains("cleanup failed"));
        assertTrue(json.get("cleanup").toString().contains("unresolvedFixtureIds"));
    }

    @Test
    void fixtureSpawnFailedResultCarriesFixtureAndRole() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                """
                {
                  "version": 1,
                  "requestId": "spawn_bad",
                  "assetId": "Asset",
                  "roleId": "Role",
                  "ticks": 5,
                  "fixtures": {
                    "list": [
                      {"fixtureId": "npcUnderTest", "kind": "npcUnderTest", "roleId": "Role"},
                      {"fixtureId": "npc.helper", "kind": "npc", "roleId": "MissingRole"}
                    ]
                  }
                }
                """,
                config
        );
        NpcRuntimeFixtureSpec fixture = request.fixtures().list().get(1);
        NpcRuntimeCleanupReport cleanupReport = NpcRuntimeCleanupReport.builder()
                .entityRemoval(true)
                .build();
        NpcRuntimeFixtureSpawnResult spawnResult = NpcRuntimeFixtureSpawnResult.failed(fixture, "Unknown NPC role: MissingRole");

        NpcRuntimeResult result = NpcRuntimeResult.fixtureSpawnFailed(
                request,
                1,
                Path.of("spawn_bad.trace.jsonl"),
                spawnResult,
                cleanupReport
        );

        Map<String, Object> json = NpcRuntimeJson.parseObject(result.toJson());
        assertEquals("failed", json.get("status"));
        assertEquals("fixture-spawn-failed", json.get("classification"));
        assertEquals(1, ((Number) json.get("ticksRun")).intValue());
        assertTrue(json.get("error").toString().contains("npc.helper"));
        assertTrue(json.get("error").toString().contains("MissingRole"));
        assertTrue(json.get("summary").toString().contains("fixtureSpawn"));
    }
}
