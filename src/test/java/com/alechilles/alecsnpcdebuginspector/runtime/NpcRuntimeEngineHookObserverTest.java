package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeEngineHookObserverTest {
    @Test
    void emitsRequestedEngineHookEvidenceFromSnapshotSections() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Targeting / Sensors ===
                        - Marked Target Slots: 2
                        - Target LockedTarget: Enemy (11111111-1111-1111-1111-111111111111)
                        - Target Focus: <none>

                        === Pathing ===
                        - Following Path: true
                        - Last Waypoint: (4.00, 70.00, 3.00)
                        - Nav State: FOLLOWING
                        - Obstructed: false

                        === AI ===
                        - Current Tree Step: MeleeAttack

                        === Combat ===
                        - Executing Attack: true
                        """
        ));
        NpcRuntimeRequest.EngineHooksSpec hooks = new NpcRuntimeRequest.EngineHooksSpec(true, true, true, true);

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeEngineHookObserver()
                .traceRecords("request-a", 5, "npc_under_test", observed, null, hooks);

        NpcRuntimeTraceRecord target = first(records, "target-selection-evidence");
        assertEquals("npc_under_test", target.fields().get("npcId"));
        assertEquals("targetLockedtarget", target.fields().get("targetId"));
        assertEquals(2, target.fields().get("candidateCount"));
        assertEquals(true, target.fields().get("selected"));
        assertEquals("snapshot-target-slot", target.fields().get("reason"));

        NpcRuntimeTraceRecord pathing = first(records, "pathing-evidence");
        assertEquals(List.of(4.0, 70.0, 3.0), pathing.fields().get("destination"));
        assertEquals("FOLLOWING", pathing.fields().get("status"));
        assertEquals(false, pathing.fields().get("stuck"));

        NpcRuntimeTraceRecord combat = first(records, "combat-eligibility-evidence");
        assertEquals("combatSupport.executingAttack", combat.fields().get("actionId"));
        assertEquals(true, combat.fields().get("eligible"));
        assertEquals("snapshot-combat-support", combat.fields().get("reason"));

        NpcRuntimeTraceRecord instruction = first(records, "instruction-lifecycle-evidence");
        assertEquals("MeleeAttack", instruction.fields().get("instructionId"));
        assertEquals("selected", instruction.fields().get("status"));
    }

    @Test
    void emitsExplicitUnavailableRecordsWhenRequestedHookHasNoSourceData() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === AI ===
                        - Status: Role unavailable
                        """
        ));
        NpcRuntimeRequest.EngineHooksSpec hooks = new NpcRuntimeRequest.EngineHooksSpec(true, true, true, true);

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeEngineHookObserver()
                .traceRecords("request-a", 5, "npc_under_test", observed, null, hooks);

        assertTrue(String.valueOf(first(records, "target-selection-evidence").fields().get("reason")).startsWith("unavailable:"));
        assertTrue(String.valueOf(first(records, "pathing-evidence").fields().get("status")).startsWith("unavailable:"));
        assertTrue(String.valueOf(first(records, "combat-eligibility-evidence").fields().get("reason")).startsWith("unavailable:"));
        assertTrue(String.valueOf(first(records, "instruction-lifecycle-evidence").fields().get("status")).startsWith("unavailable:"));
        assertEquals(4, records.size());
    }

    @Test
    void correlatesTargetSelectionToKnownFixtureAndPositions() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Targeting / Sensors ===
                        - Marked Target Slots: 1
                        - Target Enemy: Target Dummy (22222222-2222-2222-2222-222222222222)
                        """
        ));
        NpcRuntimeFixtureRegistry registry = new NpcRuntimeFixtureRegistry();
        registry.recordEntity("npcUnderTest", "npcUnderTest", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        registry.recordEntity("target.enemy", "targetDummy", UUID.fromString("22222222-2222-2222-2222-222222222222"));

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeEngineHookObserver()
                .traceRecords(
                        "request-a",
                        5,
                        "npc_under_test",
                        observed,
                        null,
                        new NpcRuntimeRequest.EngineHooksSpec(true, false, false, false),
                        registry,
                        List.of(
                                fixture("npcUnderTest", "npcUnderTest", List.of(0, 64, 0), null),
                                fixture("target.enemy", "targetDummy", List.of(3, 64, 4), "Enemy")
                        )
                );

        NpcRuntimeTraceRecord target = first(records, "target-selection-evidence");
        assertEquals("targetEnemy", target.fields().get("targetId"));
        assertEquals("22222222-2222-2222-2222-222222222222", target.fields().get("selectedTargetUuid"));
        assertEquals("target.enemy", target.fields().get("selectedFixtureId"));
        assertEquals(List.of(0.0, 64.0, 0.0), target.fields().get("npcPosition"));
        assertEquals(List.of(3.0, 64.0, 4.0), target.fields().get("targetPosition"));
        assertEquals(5.0, target.fields().get("distance"));
        assertEquals("snapshot-target-slot: fixture-correlated", target.fields().get("reason"));
    }

    @Test
    void reportsUnknownTargetUuidWithoutFixtureCorrelation() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Targeting / Sensors ===
                        - Target Enemy: Target Dummy (33333333-3333-3333-3333-333333333333)
                        """
        ));

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeEngineHookObserver()
                .traceRecords(
                        "request-a",
                        5,
                        "npc_under_test",
                        observed,
                        null,
                        new NpcRuntimeRequest.EngineHooksSpec(true, false, false, false),
                        new NpcRuntimeFixtureRegistry(),
                        List.of(fixture("npcUnderTest", "npcUnderTest", List.of(0, 64, 0), null))
                );

        NpcRuntimeTraceRecord target = first(records, "target-selection-evidence");
        assertEquals("33333333-3333-3333-3333-333333333333", target.fields().get("selectedTargetUuid"));
        assertEquals(null, target.fields().get("selectedFixtureId"));
        assertEquals("snapshot-target-slot: unknown-target-uuid", target.fields().get("reason"));
    }

    @Test
    void reportsEmptyTargetSlotWithoutSelectedFixture() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Targeting / Sensors ===
                        - Marked Target Slots: 0
                        - Target Enemy: <none>
                        """
        ));

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeEngineHookObserver()
                .traceRecords(
                        "request-a",
                        5,
                        "npc_under_test",
                        observed,
                        null,
                        new NpcRuntimeRequest.EngineHooksSpec(true, false, false, false),
                        new NpcRuntimeFixtureRegistry(),
                        List.of()
                );

        NpcRuntimeTraceRecord target = first(records, "target-selection-evidence");
        assertEquals(false, target.fields().get("selected"));
        assertEquals(null, target.fields().get("selectedFixtureId"));
        assertEquals("snapshot-target-slot: empty", target.fields().get("reason"));
    }

    private static NpcRuntimeTraceRecord first(List<NpcRuntimeTraceRecord> records, String kind) {
        return records.stream()
                .filter(record -> kind.equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
    }

    private static NpcRuntimeFixtureSpec fixture(String fixtureId, String kind, List<Object> position, String targetSlot) {
        return NpcRuntimeFixtureSpec.fromMap(
                Map.ofEntries(
                        Map.entry("fixtureId", fixtureId),
                        Map.entry("kind", kind),
                        Map.entry("position", position),
                        Map.entry("roleId", "Role"),
                        Map.entry("targetSlot", targetSlot != null ? targetSlot : "")
                ),
                "request-a",
                "fixtures.list[]",
                "Role"
        );
    }
}
