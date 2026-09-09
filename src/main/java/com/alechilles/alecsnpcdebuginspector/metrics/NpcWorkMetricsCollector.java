package com.alechilles.alecsnpcdebuginspector.metrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Maintains bounded rolling NPC work metrics keyed by NPC UUID or runtime fixture id.
 */
public final class NpcWorkMetricsCollector {
    private static final int MAX_TOP_EVENT_RECORD_KINDS = 8;

    private final int windowTicks;
    private final NpcWorkMetricWeights weights;
    private final Map<String, ArrayDeque<NpcWorkMetricSample>> samplesByNpc = new HashMap<>();

    public NpcWorkMetricsCollector(int windowTicks, @Nonnull NpcWorkMetricWeights weights) {
        this.windowTicks = Math.max(1, windowTicks);
        this.weights = weights;
    }

    public int windowTicks() {
        return windowTicks;
    }

    public synchronized void record(@Nonnull NpcWorkMetricSample sample) {
        ArrayDeque<NpcWorkMetricSample> samples = samplesByNpc.computeIfAbsent(sample.npcId(), ignored -> new ArrayDeque<>());
        samples.addLast(sample);
        int minimumTick = sample.tick() - windowTicks + 1;
        while (!samples.isEmpty() && samples.peekFirst().tick() < minimumTick) {
            samples.removeFirst();
        }
    }

    @Nonnull
    public synchronized NpcWorkMetricSnapshot snapshot(@Nonnull String npcId) {
        ArrayDeque<NpcWorkMetricSample> samples = samplesByNpc.get(npcId);
        if (samples == null || samples.isEmpty()) {
            return emptySnapshot(npcId);
        }
        Totals totals = new Totals();
        for (NpcWorkMetricSample sample : samples) {
            totals.add(sample, score(sample));
        }
        return totals.toSnapshot(npcId, windowTicks, samples.size());
    }

    private NpcWorkMetricSnapshot emptySnapshot(@Nonnull String npcId) {
        return new NpcWorkMetricSnapshot(
                npcId,
                windowTicks,
                0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                List.of(),
                Map.of(),
                List.of()
        );
    }

    private double score(@Nonnull NpcWorkMetricSample sample) {
        return sample.sensorChecks() * weights.sensor()
                + sample.targetSelections() * weights.targetSelection()
                + sample.targetCandidates() * weights.targetCandidate()
                + sample.pathingEvents() * weights.pathing()
                + sample.combatEligibilityChecks() * weights.combatEligibility()
                + sample.instructionChanges() * weights.instructionChange()
                + sample.actionTransitions() * weights.actionTransition()
                + sample.stateTransitions() * weights.stateTransition()
                + sample.flockSignalEvents() * weights.flockSignal();
    }

    private final class Totals {
        int eventRecords;
        int sensorChecks;
        int targetSelections;
        int targetCandidates;
        int pathingEvents;
        int combatEligibilityChecks;
        int instructionChanges;
        int actionTransitions;
        int stateTransitions;
        int flockSignalEvents;
        double idleScore;
        final Map<String, Integer> eventRecordKinds = new HashMap<>();

        void add(@Nonnull NpcWorkMetricSample sample, double sampleScore) {
            eventRecords += sample.eventRecords();
            sensorChecks += sample.sensorChecks();
            targetSelections += sample.targetSelections();
            targetCandidates += sample.targetCandidates();
            pathingEvents += sample.pathingEvents();
            combatEligibilityChecks += sample.combatEligibilityChecks();
            instructionChanges += sample.instructionChanges();
            actionTransitions += sample.actionTransitions();
            stateTransitions += sample.stateTransitions();
            flockSignalEvents += sample.flockSignalEvents();
            for (Map.Entry<String, Integer> entry : sample.eventRecordKinds().entrySet()) {
                eventRecordKinds.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            if (sample.idle()) {
                idleScore += sampleScore;
            }
        }

        NpcWorkMetricSnapshot toSnapshot(@Nonnull String npcId, int windowTicks, int samples) {
            LinkedHashMap<String, Double> scores = new LinkedHashMap<>();
            scores.put("targeting", targetSelections * weights.targetSelection() + targetCandidates * weights.targetCandidate());
            scores.put("pathing", pathingEvents * weights.pathing());
            scores.put("combat", combatEligibilityChecks * weights.combatEligibility());
            scores.put("sensors", sensorChecks * weights.sensor());
            scores.put("instructions", instructionChanges * weights.instructionChange() + actionTransitions * weights.actionTransition());
            scores.put("signals", flockSignalEvents * weights.flockSignal());
            scores.put("state", stateTransitions * weights.stateTransition());
            double workScore = scores.values().stream().mapToDouble(Double::doubleValue).sum();
            List<NpcWorkMetricSnapshot.Contributor> contributors = new ArrayList<>();
            for (Map.Entry<String, Double> entry : scores.entrySet()) {
                if (entry.getValue() > 0.0) {
                    contributors.add(new NpcWorkMetricSnapshot.Contributor(entry.getKey(), entry.getValue() / windowTicks));
                }
            }
            contributors.sort(Comparator.comparingDouble(NpcWorkMetricSnapshot.Contributor::score).reversed());
            LinkedHashMap<String, Double> eventRecordKindRates = new LinkedHashMap<>();
            eventRecordKinds.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .forEach(entry -> eventRecordKindRates.put(entry.getKey(), entry.getValue() / (double) windowTicks));
            List<NpcWorkMetricSnapshot.EventRecordKindRate> topEventRecordKinds = eventRecordKindRates.entrySet().stream()
                    .limit(MAX_TOP_EVENT_RECORD_KINDS)
                    .map(entry -> new NpcWorkMetricSnapshot.EventRecordKindRate(entry.getKey(), entry.getValue()))
                    .toList();
            return new NpcWorkMetricSnapshot(
                    npcId,
                    windowTicks,
                    samples,
                    workScore / windowTicks,
                    eventRecords / (double) windowTicks,
                    sensorChecks / (double) windowTicks,
                    targetSelections / (double) windowTicks,
                    targetCandidates / (double) windowTicks,
                    pathingEvents / (double) windowTicks,
                    combatEligibilityChecks / (double) windowTicks,
                    instructionChanges / (double) windowTicks,
                    actionTransitions / (double) windowTicks,
                    stateTransitions / (double) windowTicks,
                    flockSignalEvents / (double) windowTicks,
                    idleScore / windowTicks,
                    List.copyOf(contributors),
                    eventRecordKindRates,
                    topEventRecordKinds
            );
        }
    }
}
