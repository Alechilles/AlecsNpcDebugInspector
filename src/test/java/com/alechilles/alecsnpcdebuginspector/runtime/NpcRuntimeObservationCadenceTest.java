package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeObservationCadenceTest {
    @Test
    void recordsSnapshotsOnlyOnRequestedTicks() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"cadence\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5,"
                        + "\"record\":{\"profile\":\"full\",\"everyTicks\":2,\"includeSnapshots\":true,\"includeEvents\":true}}",
                config
        );

        NpcRuntimeObservationCadence cadence = NpcRuntimeObservationCadence.from(request);

        assertTrue(cadence.shouldRecordEvent("tick-start", 1));
        assertTrue(cadence.includeEvents());
        assertTrue(cadence.includeSnapshots());
        assertTrue(cadence.shouldRecordSnapshot(0));
        assertFalse(cadence.shouldRecordSnapshot(1));
        assertTrue(cadence.shouldRecordSnapshot(2));
        assertFalse(cadence.shouldRecordSnapshot(3));
        assertTrue(cadence.shouldRecordSnapshot(4));
    }

    @Test
    void disablesSnapshotsWhenRequestDisablesThem() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"cadence\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5,"
                        + "\"record\":{\"everyTicks\":1,\"includeSnapshots\":false,\"includeEvents\":false}}",
                config
        );

        NpcRuntimeObservationCadence cadence = NpcRuntimeObservationCadence.from(request);

        assertFalse(cadence.includeEvents());
        assertFalse(cadence.includeSnapshots());
        assertFalse(cadence.shouldRecordEvent("run-start", 0));
        assertFalse(cadence.shouldRecordSnapshot(0));
        assertFalse(cadence.shouldRecordSnapshot(1));
    }

    @Test
    void standardProfileRecordsLifecycleButSuppressesPerTickNoise() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"cadence\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5,"
                        + "\"record\":{\"profile\":\"standard\",\"everyTicks\":3,\"includeSnapshots\":true,\"includeEvents\":true}}",
                config
        );

        NpcRuntimeObservationCadence cadence = NpcRuntimeObservationCadence.from(request);

        assertTrue(cadence.shouldRecordEvent("run-start", 0));
        assertTrue(cadence.shouldRecordEvent("world-ready", 0));
        assertTrue(cadence.shouldRecordEvent("fixture-spawn", 0));
        assertTrue(cadence.shouldRecordEvent("assertions", 5));
        assertFalse(cadence.shouldRecordEvent("tick-start", 1));
        assertFalse(cadence.shouldRecordEvent("npc-sensor-delta", 3));
        assertTrue(cadence.shouldRecordSnapshot(3));
    }

    @Test
    void minimalProfileRecordsOnlyTerminalLifecycleEvents() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"cadence\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5,"
                        + "\"record\":{\"profile\":\"minimal\",\"includeSnapshots\":false,\"includeEvents\":true}}",
                config
        );

        NpcRuntimeObservationCadence cadence = NpcRuntimeObservationCadence.from(request);

        assertTrue(cadence.shouldRecordEvent("run-start", 0));
        assertTrue(cadence.shouldRecordEvent("assertions-resolved", 4));
        assertTrue(cadence.shouldRecordEvent("run-end", 4));
        assertFalse(cadence.shouldRecordEvent("world-ready", 0));
        assertFalse(cadence.shouldRecordEvent("fixture-spawn", 0));
        assertFalse(cadence.shouldRecordEvent("tick-start", 1));
    }
}
