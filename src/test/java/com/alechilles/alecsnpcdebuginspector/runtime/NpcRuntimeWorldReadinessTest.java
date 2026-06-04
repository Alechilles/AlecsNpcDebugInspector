package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeWorldReadinessTest {
    @Test
    void readyStateSerializesForStatus() {
        NpcRuntimeWorldReadiness readiness = NpcRuntimeWorldReadiness.ready("npc_runtime_test_flatworld", 0);

        Map<String, Object> map = readiness.toMap();

        assertEquals(true, map.get("ready"));
        assertEquals("npc_runtime_test_flatworld", map.get("worldId"));
        assertEquals(true, map.get("ticking"));
        assertEquals(false, map.get("paused"));
        assertEquals(0, map.get("playerCount"));
        assertEquals("ready", map.get("reason"));
        assertFalse(readiness.shouldFailQueuedRequests());
    }

    @Test
    void startupReadinessFailuresDoNotImmediatelyFailQueuedRequests() {
        NpcRuntimeWorldReadiness readiness = NpcRuntimeWorldReadiness.notReady(
                "npc_runtime_test_flatworld",
                NpcRuntimeWorldReadiness.UNIVERSE_NOT_READY,
                "Universe startup future is not complete"
        );

        assertFalse(readiness.ready());
        assertFalse(readiness.shouldFailQueuedRequests());
        assertEquals("universe-not-ready: Universe startup future is not complete", readiness.displayReason());
    }

    @Test
    void terminalWorldFailuresFailQueuedRequests() {
        NpcRuntimeWorldReadiness readiness = NpcRuntimeWorldReadiness.fromWorld(
                "npc_runtime_test_flatworld",
                "npc_runtime_test_flatworld",
                false,
                false,
                0
        );

        assertFalse(readiness.ready());
        assertTrue(readiness.shouldFailQueuedRequests());
        assertEquals("world-not-ticking", readiness.reason());
        assertEquals(false, readiness.ticking());
    }
}
