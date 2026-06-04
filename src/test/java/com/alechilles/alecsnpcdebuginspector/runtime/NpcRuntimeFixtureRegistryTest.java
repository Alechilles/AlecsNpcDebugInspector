package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeFixtureRegistryTest {
    @Test
    void recordsEntityFixturesInInsertionOrder() {
        NpcRuntimeFixtureRegistry registry = new NpcRuntimeFixtureRegistry();
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

        registry.recordEntity("npcUnderTest", "npcUnderTest", uuid);

        assertTrue(registry.contains("npcUnderTest"));
        assertEquals(1, registry.fixtures().size());
        Map<String, Object> json = registry.fixtures().getFirst().toMap();
        assertEquals("npcUnderTest", json.get("fixtureId"));
        assertEquals(uuid.toString(), json.get("uuid"));
    }

    @Test
    void rejectsDuplicateFixtureIdsAcrossEntityAndBlockRecords() {
        NpcRuntimeFixtureRegistry registry = new NpcRuntimeFixtureRegistry();
        registry.recordEntity("fixture", "npc", null);

        assertThrows(IllegalArgumentException.class, () -> registry.recordBlockMutation("fixture", "0,64,0", "Air"));
    }

    @Test
    void recordsFixtureLinksInInsertionOrder() {
        NpcRuntimeFixtureRegistry registry = new NpcRuntimeFixtureRegistry();

        registry.recordFixtureLink("flock.child", "flockLeader", "npcUnderTest");
        registry.recordFixtureLink("family.child", "parent", "family.parent");

        assertEquals(2, registry.fixtureLinks().size());
        Map<String, Object> first = registry.fixtureLinks().getFirst().toMap();
        assertEquals("flock.child", first.get("fixtureId"));
        assertEquals("flockLeader", first.get("relationship"));
        assertEquals("npcUnderTest", first.get("targetFixtureId"));
    }
}
