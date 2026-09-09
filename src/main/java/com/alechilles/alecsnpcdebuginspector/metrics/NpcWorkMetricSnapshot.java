package com.alechilles.alecsnpcdebuginspector.metrics;

import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Rolling metrics summary for a single NPC.
 */
public record NpcWorkMetricSnapshot(
        @Nonnull String npcId,
        int windowTicks,
        int samples,
        double workScorePerTick,
        double eventRecordsPerTick,
        double sensorChecksPerTick,
        double targetSelectionsPerTick,
        double targetCandidatesPerTick,
        double pathingEventsPerTick,
        double combatEligibilityPerTick,
        double instructionChangesPerTick,
        double actionTransitionsPerTick,
        double stateTransitionsPerTick,
        double flockSignalEventsPerTick,
        double idleChurnScore,
        @Nonnull List<Contributor> topContributors,
        @Nonnull Map<String, Double> eventRecordKindsPerTick,
        @Nonnull List<EventRecordKindRate> topEventRecordKinds
) {
    public record Contributor(@Nonnull String category, double score) {
    }

    public record EventRecordKindRate(@Nonnull String kind, double recordsPerTick) {
    }
}
