package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeTickSchedulerTest {
    @Test
    void advancesOneExecutorStepPerTick() throws Exception {
        NpcRuntimeScenarioRun run = NpcRuntimeScenarioRun.start("request-a", "world-a", 3);
        NpcRuntimeTickScheduler scheduler = new NpcRuntimeTickScheduler();
        List<String> events = new ArrayList<>();

        NpcRuntimeTickScheduler.RunSummary summary = scheduler.run(run, step -> {
            events.add("execute");
            return step.run();
        }, tick -> {
            events.add("tick-" + tick);
            return NpcRuntimeTickScheduler.TickOutcome.continueRunning();
        });

        assertEquals(List.of("execute", "tick-0", "execute", "tick-1", "execute", "tick-2"), events);
        assertEquals(3, summary.ticksRun());
        assertFalse(summary.canceled());
        assertEquals(2, run.currentTick());
    }

    @Test
    void stopsWhenRunIsCanceled() throws Exception {
        NpcRuntimeScenarioRun run = NpcRuntimeScenarioRun.start("request-a", "world-a", 5);
        NpcRuntimeTickScheduler scheduler = new NpcRuntimeTickScheduler();
        List<Integer> ticks = new ArrayList<>();

        NpcRuntimeTickScheduler.RunSummary summary = scheduler.run(run, NpcRuntimeTickScheduler.StepExecutor.direct(), tick -> {
            ticks.add(tick);
            if (tick == 1) {
                run.cancel("test cancellation");
                return NpcRuntimeTickScheduler.TickOutcome.canceled("test cancellation");
            }
            return NpcRuntimeTickScheduler.TickOutcome.continueRunning();
        });

        assertEquals(List.of(0, 1), ticks);
        assertEquals(2, summary.ticksRun());
        assertTrue(summary.canceled());
        assertEquals("test cancellation", summary.cancelReason());
    }

    @Test
    void tracksNpcIdsFixturesAndCleanupState() {
        NpcRuntimeScenarioRun run = NpcRuntimeScenarioRun.start("request-a", "world-a", 5);
        UUID npcUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

        run.addNpc("npcUnderTest", npcUuid);
        run.addFixture("target_1");
        run.markCleanup(true, "removed 2 fixtures");

        assertEquals("request-a", run.requestId());
        assertEquals("world-a", run.worldId());
        assertEquals(5, run.deadlineTick());
        assertEquals(npcUuid, run.npcUuids().get("npcUnderTest"));
        assertEquals(List.of("target_1"), run.fixtures());
        assertTrue(run.cleanupAttempted());
        assertTrue(run.cleanupSucceeded());
        assertEquals("removed 2 fixtures", run.cleanupMessage());
    }
}
