package com.alechilles.alecsnpcdebuginspector.runtime;

import javax.annotation.Nonnull;

/**
 * Determines which per-tick runtime observations should be written.
 */
public record NpcRuntimeObservationCadence(
        @Nonnull String profile,
        int everyTicks,
        boolean includeSnapshots,
        boolean includeEvents,
        long maxTraceBytes
) {
    @Nonnull
    public static NpcRuntimeObservationCadence from(@Nonnull NpcRuntimeRequest request) {
        return new NpcRuntimeObservationCadence(
                request.record().profile(),
                Math.max(1, request.record().everyTicks()),
                request.record().includeSnapshots(),
                request.record().includeEvents(),
                request.limits().maxTraceBytes()
        );
    }

    public boolean shouldRecordSnapshot(int tick) {
        return includeSnapshots && tick >= 0 && tick % everyTicks == 0;
    }

    public boolean shouldRecordEvent(@Nonnull String kind, int tick) {
        if (!includeEvents || tick < 0) {
            return false;
        }
        return switch (profile) {
            case "minimal" -> isMinimalEvent(kind);
            case "standard" -> isMinimalEvent(kind) || isStandardEvent(kind);
            default -> true;
        };
    }

    private static boolean isMinimalEvent(@Nonnull String kind) {
        return switch (kind) {
            case "run-start", "run-end", "assertions", "assertions-resolved", "cleanup" -> true;
            default -> false;
        };
    }

    private static boolean isStandardEvent(@Nonnull String kind) {
        return switch (kind) {
            case "world-ready", "arena-reset", "fixture-spawn", "fixture-link" -> true;
            default -> false;
        };
    }
}
