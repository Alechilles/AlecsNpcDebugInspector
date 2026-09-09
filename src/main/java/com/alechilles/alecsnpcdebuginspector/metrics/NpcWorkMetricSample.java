package com.alechilles.alecsnpcdebuginspector.metrics;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * One bounded observation sample for a single NPC.
 */
public record NpcWorkMetricSample(
        String npcId,
        int tick,
        int eventRecords,
        int sensorChecks,
        int targetSelections,
        int targetCandidates,
        int pathingEvents,
        int combatEligibilityChecks,
        int instructionChanges,
        int actionTransitions,
        int stateTransitions,
        int flockSignalEvents,
        @Nonnull Map<String, Integer> eventRecordKinds,
        boolean idle
) {
    public NpcWorkMetricSample {
        eventRecordKinds = Map.copyOf(eventRecordKinds);
    }
}
