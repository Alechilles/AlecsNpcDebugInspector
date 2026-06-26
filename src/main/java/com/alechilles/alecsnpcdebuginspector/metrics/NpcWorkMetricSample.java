package com.alechilles.alecsnpcdebuginspector.metrics;

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
        boolean idle
) {
}
