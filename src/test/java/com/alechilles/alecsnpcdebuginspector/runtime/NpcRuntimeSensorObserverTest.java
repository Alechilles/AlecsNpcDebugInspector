package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeSensorObserverTest {
    @Test
    void emitsTargetSlotAndTimerSensorEvidence() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(
                null,
                new NpcDebugSnapshot(
                        "NPC Debug Inspector",
                        "",
                        """
                                === Targeting / Sensors ===
                                - Target LockedTarget: <none>
                                - Target MasterTarget: Tamework Example (abc)
                                - Sensor Scope Keys: 2

                                === Timers / Cooldowns ===
                                - Attack Executing: false
                                """
                )
        );

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeSensorObserver().traceRecords("request-a", 2, observed);

        assertEquals(4, records.size());
        assertTrue(records.stream().allMatch(record -> "sensor-evidence".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "TargetSlot".equals(record.fields().get("sensorType"))
                && "not-matched".equals(record.fields().get("matchResult"))));
        assertTrue(records.stream().anyMatch(record -> "TargetSlot".equals(record.fields().get("sensorType"))
                && "matched".equals(record.fields().get("matchResult"))));
        assertTrue(records.stream().anyMatch(record -> record.toJson().contains("unsupportedFields")));
        assertTrue(records.stream().anyMatch(record -> "Timer".equals(record.fields().get("sensorType"))));
    }
}
