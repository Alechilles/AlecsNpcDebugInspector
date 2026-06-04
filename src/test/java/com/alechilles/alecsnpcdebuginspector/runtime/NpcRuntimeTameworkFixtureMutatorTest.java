package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeTameworkFixtureMutatorTest {
    @Test
    void appliesSupportedTameworkMutationsAndEmitsTraceRecords() {
        FakeBridge bridge = new FakeBridge();
        NpcRuntimeTameworkFixtureMutator mutator = new NpcRuntimeTameworkFixtureMutator(bridge);
        NpcRuntimeFixtureSpec.TameworkMutation mutation = new NpcRuntimeFixtureSpec.TameworkMutation(
                true,
                Map.of("type", "syntheticPlayer", "id", "owner_a"),
                Map.of("hunger", 80, "thirst", 60),
                List.of("tamework:well_fed"),
                null,
                "adult"
        );
        NpcRuntimeFixtureSpawner.SpawnedNpc spawned = new NpcRuntimeFixtureSpawner.SpawnedNpc(
                "npc_under_test",
                NpcRuntimeFixtureKind.NPC_UNDER_TEST,
                "Role",
                null,
                null,
                null
        );

        List<NpcRuntimeTraceRecord> records = mutator.apply("request-a", 0, null, spawned, mutation);

        assertEquals(true, bridge.tamed);
        assertEquals(Map.of("type", "syntheticPlayer", "id", "owner_a"), bridge.owner);
        assertEquals(80.0, bridge.needs.get("hunger"));
        assertEquals(60.0, bridge.needs.get("thirst"));
        assertEquals("tamework:well_fed", bridge.effectId);
        assertEquals("adult", bridge.lifeStage);
        assertEquals(6, records.size());
        assertTrue(records.get(0).toJson().contains("\"field\":\"tamed\""));
        assertTrue(records.get(0).toJson().contains("\"status\":\"applied\""));
        assertTrue(records.get(2).toJson().contains("\"field\":\"needs.hunger\""));
        assertTrue(records.get(4).toJson().contains("\"field\":\"effects\""));
        assertTrue(records.get(5).toJson().contains("\"field\":\"lifeStage\""));
    }

    @Test
    void reportsUnsupportedTameworkMutationsWithoutDroppingEvidence() {
        FakeBridge bridge = new FakeBridge();
        bridge.supportLifeStage = false;
        NpcRuntimeTameworkFixtureMutator mutator = new NpcRuntimeTameworkFixtureMutator(bridge);
        NpcRuntimeFixtureSpec.TameworkMutation mutation = new NpcRuntimeFixtureSpec.TameworkMutation(
                null,
                Map.of(),
                Map.of(),
                List.of(),
                "follow",
                "adult"
        );
        NpcRuntimeFixtureSpawner.SpawnedNpc spawned = new NpcRuntimeFixtureSpawner.SpawnedNpc(
                "npc_under_test",
                NpcRuntimeFixtureKind.NPC_UNDER_TEST,
                "Role",
                null,
                null,
                null
        );

        List<NpcRuntimeTraceRecord> records = mutator.apply("request-a", 0, null, spawned, mutation);

        assertEquals(2, records.size());
        assertTrue(records.get(0).toJson().contains("\"field\":\"commandState\""));
        assertTrue(records.get(0).toJson().contains("\"status\":\"unsupported\""));
        assertTrue(records.get(1).toJson().contains("\"field\":\"lifeStage\""));
        assertTrue(records.get(1).toJson().contains("\"status\":\"unsupported\""));
        assertTrue(records.get(1).toJson().contains("\"unsupportedFields\":[\"lifeStage\"]"));
    }

    private static final class FakeBridge implements NpcRuntimeTameworkFixtureMutator.MutationBridge {
        boolean supportLifeStage = true;
        Boolean tamed;
        Map<String, Object> owner;
        final java.util.LinkedHashMap<String, Double> needs = new java.util.LinkedHashMap<>();
        String effectId;
        String lifeStage;

        @Override
        public NpcRuntimeTameworkFixtureMutator.MutationOutcome setTamed(Object store,
                                                                         NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                                                         boolean value) {
            tamed = value;
            return NpcRuntimeTameworkFixtureMutator.MutationOutcome.applied(value);
        }

        @Override
        public NpcRuntimeTameworkFixtureMutator.MutationOutcome setOwner(Object store,
                                                                         NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                                                         Map<String, Object> value) {
            owner = value;
            return NpcRuntimeTameworkFixtureMutator.MutationOutcome.applied(value);
        }

        @Override
        public NpcRuntimeTameworkFixtureMutator.MutationOutcome setNeed(Object store,
                                                                        NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                                                        String key,
                                                                        double value) {
            needs.put(key, value);
            return NpcRuntimeTameworkFixtureMutator.MutationOutcome.applied(value);
        }

        @Override
        public NpcRuntimeTameworkFixtureMutator.MutationOutcome applyEffect(Object store,
                                                                            NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                                                            String effectId) {
            this.effectId = effectId;
            return NpcRuntimeTameworkFixtureMutator.MutationOutcome.applied(effectId);
        }

        @Override
        public NpcRuntimeTameworkFixtureMutator.MutationOutcome setCommandState(Object store,
                                                                                NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                                                                String value) {
            return NpcRuntimeTameworkFixtureMutator.MutationOutcome.unsupported("commandState");
        }

        @Override
        public NpcRuntimeTameworkFixtureMutator.MutationOutcome setLifeStage(Object store,
                                                                             NpcRuntimeFixtureSpawner.SpawnedNpc spawned,
                                                                             String value) {
            if (!supportLifeStage) {
                return NpcRuntimeTameworkFixtureMutator.MutationOutcome.unsupported("lifeStage");
            }
            lifeStage = value;
            return NpcRuntimeTameworkFixtureMutator.MutationOutcome.applied(value);
        }
    }
}
