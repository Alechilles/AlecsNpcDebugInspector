package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.List;
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

    private static NpcRuntimeTraceRecord first(List<NpcRuntimeTraceRecord> records, String kind) {
        return records.stream()
                .filter(record -> kind.equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
    }
}
