package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeFixtureSpawnResultTest {
    @Test
    void serializesSuccessfulSpawnResult() {
        UUID uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");

        NpcRuntimeFixtureSpawnResult result = new NpcRuntimeFixtureSpawnResult(
                "target.Enemy",
                NpcRuntimeFixtureKind.TARGET_DUMMY,
                true,
                uuid,
                "EnemyRole",
                "Enemy",
                "spawned"
        );

        Map<String, Object> json = result.toMap();

        assertEquals("target.Enemy", json.get("fixtureId"));
        assertEquals("targetDummy", json.get("kind"));
        assertEquals(true, json.get("spawned"));
        assertEquals(uuid.toString(), json.get("entityUuid"));
        assertEquals("EnemyRole", json.get("roleId"));
        assertEquals("Enemy", json.get("targetSlot"));
        assertEquals("spawned", json.get("message"));
    }

    @Test
    void serializesFailedSpawnResult() {
        NpcRuntimeFixtureSpec fixture = NpcRuntimeFixtureSpec.fromMap(
                Map.of(
                        "fixtureId", "npc.helper",
                        "kind", "npc",
                        "roleId", "MissingRole"
                ),
                "spawn_failed",
                "fixtures.list[1]",
                "FallbackRole"
        );

        NpcRuntimeFixtureSpawnResult result = NpcRuntimeFixtureSpawnResult.failed(fixture, "Unknown NPC role: MissingRole");
        Map<String, Object> json = result.toMap();

        assertEquals("npc.helper", json.get("fixtureId"));
        assertEquals("npc", json.get("kind"));
        assertEquals(false, json.get("spawned"));
        assertFalse(json.containsKey("entityUuid"));
        assertEquals("MissingRole", json.get("roleId"));
        assertEquals("Unknown NPC role: MissingRole", json.get("message"));
    }

    @Test
    void fixtureSpawnTraceRecordIncludesSpawnContractFields() {
        NpcRuntimeFixtureSpec fixture = NpcRuntimeFixtureSpec.fromMap(
                Map.of(
                        "fixtureId", "target.Enemy",
                        "kind", "targetDummy",
                        "position", java.util.List.of(3, 64, 0),
                        "roleId", "EnemyRole",
                        "targetSlot", "Enemy"
                ),
                "spawn_trace",
                "fixtures.list[1]",
                "FallbackRole"
        );
        NpcRuntimeFixtureSpawnResult spawnResult = NpcRuntimeFixtureSpawnResult.failed(fixture, "spawn unavailable");

        NpcRuntimeTraceRecord record = NpcRuntimeLiveScenarioRunner.fixtureSpawnRecord(
                "spawn_trace",
                0,
                fixture,
                null,
                spawnResult
        );

        assertEquals("fixture-spawn", record.fields().get("kind"));
        assertEquals("target.Enemy", record.fields().get("fixtureId"));
        assertEquals("targetDummy", record.fields().get("fixtureKind"));
        assertEquals("EnemyRole", record.fields().get("roleId"));
        assertEquals("Enemy", record.fields().get("targetSlot"));
        assertEquals(java.util.List.of(3, 64, 0), record.fields().get("position"));
        assertTrue(record.fields().get("spawnResult").toString().contains("spawn unavailable"));
    }
}
