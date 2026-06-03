package com.alechilles.alecsnpcdebuginspector.runtime;

import javax.annotation.Nonnull;

/**
 * Determines which per-tick runtime observations should be written.
 */
public record NpcRuntimeObservationCadence(
        int everyTicks,
        boolean includeSnapshots,
        boolean includeEvents,
        long maxTraceBytes
) {
    @Nonnull
    public static NpcRuntimeObservationCadence from(@Nonnull NpcRuntimeRequest request) {
        return new NpcRuntimeObservationCadence(
                Math.max(1, request.record().everyTicks()),
                request.record().includeSnapshots(),
                request.record().includeEvents(),
                request.limits().maxTraceBytes()
        );
    }

    public boolean shouldRecordSnapshot(int tick) {
        return includeSnapshots && tick >= 0 && tick % everyTicks == 0;
    }
}
