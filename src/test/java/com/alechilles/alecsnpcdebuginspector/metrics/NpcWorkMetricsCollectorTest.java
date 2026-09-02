package com.alechilles.alecsnpcdebuginspector.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NpcWorkMetricsCollectorTest {
    @Test
    void snapshotCalculatesWeightedRatesAndTopContributors() {
        NpcWorkMetricsCollector collector = new NpcWorkMetricsCollector(100, NpcWorkMetricWeights.defaults());

        collector.record(new NpcWorkMetricSample(
                "npcUnderTest",
                1,
                12,
                4,
                4,
                30,
                1,
                2,
                0,
                0,
                3,
                1,
                true
        ));

        NpcWorkMetricSnapshot snapshot = collector.snapshot("npcUnderTest");

        assertEquals(1, snapshot.samples());
        assertEquals(100, snapshot.windowTicks());
        assertEquals(0.04, snapshot.sensorChecksPerTick());
        assertEquals(0.30, snapshot.targetCandidatesPerTick());
        assertEquals(0.01, snapshot.pathingEventsPerTick());
        assertTrue(snapshot.workScorePerTick() > 0.0);
        assertEquals("targeting", snapshot.topContributors().getFirst().category());
        assertTrue(snapshot.idleChurnScore() > 0.0);
    }

    @Test
    void rollingWindowEvictsOldSamples() {
        NpcWorkMetricsCollector collector = new NpcWorkMetricsCollector(3, NpcWorkMetricWeights.defaults());

        collector.record(new NpcWorkMetricSample("npc", 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, true));
        collector.record(new NpcWorkMetricSample("npc", 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, true));
        collector.record(new NpcWorkMetricSample("npc", 5, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, true));

        NpcWorkMetricSnapshot snapshot = collector.snapshot("npc");

        assertEquals(1, snapshot.samples());
        assertEquals(1.0 / 3.0, snapshot.sensorChecksPerTick());
    }
}
