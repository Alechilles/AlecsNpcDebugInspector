package com.alechilles.alecsnpcdebuginspector.debug;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDebugHistoryStoreTest {
    @Test
    void tracksChangesAndFiltersEventsByCategory() {
        NpcDebugHistoryStore store = new NpcDebugHistoryStore();
        UUID npcUuid = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-28T12:00:00Z");

        assertFalse(store.track(
                npcUuid,
                "tamework.api.profile.id",
                "Profile Id",
                "profile-alpha",
                now,
                true,
                NpcDebugEventCategory.CORE
        ).changed);

        assertTrue(store.track(
                npcUuid,
                "tamework.api.profile.id",
                "Profile Id",
                "profile-bravo",
                now.plusSeconds(5),
                true,
                NpcDebugEventCategory.CORE
        ).changed);

        store.recordEvent(
                npcUuid,
                now.plusSeconds(10),
                "Timer ended: return_home",
                NpcDebugEventCategory.TIMERS
        );

        assertEquals(2, store.totalEventCount(npcUuid));
        assertEquals(1, store.events(npcUuid, Set.of(NpcDebugEventCategory.CORE)).size());
        assertEquals(1, store.events(npcUuid, Set.of(NpcDebugEventCategory.TIMERS)).size());
        assertTrue(store.events(npcUuid, Set.of(NpcDebugEventCategory.CORE)).getFirst().contains("Profile Id"));
    }
}
