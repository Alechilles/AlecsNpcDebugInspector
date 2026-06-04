package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeObserverTest {
    @Test
    void emitsStructuredSectionRecordsFromSnapshotDetails() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc observed = observer.observe(null, sampleSnapshot("Idle"));

        List<NpcRuntimeTraceRecord> records = observer.traceRecords("request-a", 3, observed, null);

        assertTrue(records.stream().anyMatch(record -> "npc-state".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "targeting".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "timers".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "pathing".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "combat".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "components".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "flock".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "tamework".equals(record.fields().get("kind"))));
        assertEquals("Idle", observed.section("AI").get("state"));
        assertEquals("false", observed.section("Pathing").get("followingPath"));
    }

    @Test
    void emitsTransitionRecordsWhenObservedFieldsChange() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc previous = observer.observe(null, sampleSnapshot("Idle"));
        NpcRuntimeObservedNpc current = observer.observe(null, sampleSnapshot("Attack"));

        List<NpcRuntimeTraceRecord> records = observer.traceRecords("request-a", 4, current, previous);

        NpcRuntimeTraceRecord transition = records.stream()
                .filter(record -> "npc-transition".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
        assertEquals(2, transition.fields().get("changeCount"));
        assertTrue(transition.toJson().contains("\"field\":\"state\""));
        assertTrue(transition.toJson().contains("\"after\":\"Attack\""));
    }

    private static NpcDebugSnapshot sampleSnapshot(String state) {
        return new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Overview ===
                        - UUID: sample
                        - Role Id: Mob_Test
                        - State: %s

                        === Tamework ===
                        - Plugin Loaded: true
                        - Tamed Component: false

                        === AI ===
                        - State: %s
                        - Sub-State: start

                        === Targeting / Sensors ===
                        - Target LockedTarget: <none>
                        - Sensor Scope Keys: 2

                        === Pathing ===
                        - Following Path: false
                        - Nav State: AT_GOAL

                        === Timers / Cooldowns ===
                        - Attack Executing: false
                        - Scope Timer Keys: 1

                        === Combat ===
                        - Executing Attack: false
                        - Attack Override Count: 0

                        === Components ===
                        - NPCEntity: true
                        - Inventory: true

                        === Flock ===
                        - Membership: none
                        """.formatted(state, state)
        );
    }
}
