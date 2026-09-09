package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.alechilles.alecsnpcdebuginspector.metrics.NpcWorkMetricSnapshot;
import com.alechilles.alecsnpcdebuginspector.metrics.NpcWorkMetricWeights;
import com.alechilles.alecsnpcdebuginspector.metrics.NpcWorkMetricsCollector;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcLiveWorkMetricsSamplerTest {
    @Test
    void recordsLivePanelMetricsThroughHarnessObserverPipeline() {
        NpcWorkMetricsCollector collector = new NpcWorkMetricsCollector(100, NpcWorkMetricWeights.defaults());
        NpcLiveWorkMetricsSampler sampler = new NpcLiveWorkMetricsSampler(collector);
        UUID npcUuid = UUID.fromString("00000000-0000-0000-0000-000000000123");

        sampler.record(npcUuid, snapshot("Idle"));
        NpcWorkMetricSnapshot snapshot = sampler.record(npcUuid, snapshot("Alert"));

        assertEquals(2, snapshot.samples());
        assertTrue(snapshot.eventRecordKindsPerTick().containsKey("sensor-surface"));
        assertTrue(snapshot.eventRecordKindsPerTick().containsKey("target-surface"));
        assertTrue(snapshot.eventRecordKindsPerTick().containsKey("instruction-change"));
        assertTrue(snapshot.eventRecordKindsPerTick().containsKey("state-transition"));
        assertFalse(snapshot.eventRecordKindsPerTick().containsKey("npc-transition"));
        assertFalse(snapshot.eventRecordKindsPerTick().containsKey("action-start"));
        assertFalse(snapshot.eventRecordKindsPerTick().containsKey("action-change"));
        assertFalse(snapshot.eventRecordKindsPerTick().containsKey("sensor-evidence"));
        assertFalse(snapshot.eventRecordKindsPerTick().containsKey("action-evidence"));
        assertFalse(snapshot.eventRecordKindsPerTick().containsKey("combat-evaluator-evidence"));
        assertFalse(snapshot.eventRecordKindsPerTick().containsKey("instruction-lifecycle-evidence"));
        assertFalse(snapshot.eventRecordKindsPerTick().containsKey("tamework-evidence"));
        assertFalse(snapshot.eventRecordKindsPerTick().containsKey("panel-targeting"));
        assertTrue(snapshot.sensorChecksPerTick() > 0.0);
        assertEquals(0.0, snapshot.actionTransitionsPerTick());
        assertTrue(snapshot.instructionChangesPerTick() > 0.0);
        assertTrue(snapshot.stateTransitionsPerTick() > 0.0);
    }

    private static NpcDebugSnapshot snapshot(String state) {
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
                        - Current Tree Step: %sLoop
                        - Current Body Step: %sBody
                        - Transition Actions Running: false

                        === Targeting / Sensors ===
                        - Target Enemy: <none>
                        - Sensor Scope Keys: 2

                        === Pathing ===
                        - Following Path: false
                        - In Progress: false
                        - Obstructed: false

                        === Timers / Cooldowns ===
                        - Attack Executing: false
                        - Scope Timer Keys: 1

                        === Combat ===
                        - Executing Attack: false
                        - Attack Override Count: 0

                        === Alarms ===
                        - Alarm Active: false

                        === Flags ===
                        - Is Sleeping: false

                        === Components ===
                        - NPCEntity: true

                        === Flock ===
                        - Membership: none
                        """.formatted(state, state, state, state)
        );
    }
}
