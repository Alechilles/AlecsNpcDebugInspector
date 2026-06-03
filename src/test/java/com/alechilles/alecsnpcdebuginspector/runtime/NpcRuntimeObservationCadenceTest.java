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
                        + "\"record\":{\"everyTicks\":2,\"includeSnapshots\":true,\"includeEvents\":true}}",
                config
        );

        NpcRuntimeObservationCadence cadence = NpcRuntimeObservationCadence.from(request);

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
        assertFalse(cadence.shouldRecordSnapshot(0));
        assertFalse(cadence.shouldRecordSnapshot(1));
    }
}
