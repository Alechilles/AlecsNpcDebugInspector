package com.alechilles.alecsnpcdebuginspector.metrics;

/**
 * Relative weights for observable NPC work proxies.
 */
public record NpcWorkMetricWeights(
        double sensor,
        double targetSelection,
        double targetCandidate,
        double pathing,
        double combatEligibility,
        double instructionChange,
        double actionTransition,
        double stateTransition,
        double flockSignal
) {
    public static NpcWorkMetricWeights defaults() {
        return new NpcWorkMetricWeights(1.0, 1.5, 0.25, 3.0, 2.0, 2.5, 2.0, 0.5, 1.5);
    }
}
