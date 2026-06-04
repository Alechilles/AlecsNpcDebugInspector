package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeObservationDiffTest {
    @Test
    void reportsFieldLevelChangesAcrossSections() {
        NpcRuntimeObservedNpc previous = NpcRuntimeObservedNpc.fromSnapshot(null, snapshot("Idle", "false"));
        NpcRuntimeObservedNpc current = NpcRuntimeObservedNpc.fromSnapshot(null, snapshot("Attack", "true"));

        NpcRuntimeObservationDiff diff = NpcRuntimeObservationDiff.between(previous, current);

        assertTrue(diff.changed());
        assertEquals(2, diff.changes().size());
        assertTrue(diff.changes().stream().anyMatch(change -> "state".equals(change.field()) && "Attack".equals(change.after())));
        assertTrue(diff.changes().stream().anyMatch(change -> "executingAttack".equals(change.field()) && "true".equals(change.after())));
    }

    private static NpcDebugSnapshot snapshot(String state, String attackExecuting) {
        return new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "",
                """
                        === AI ===
                        - State: %s

                        === Combat ===
                        - Executing Attack: %s
                        """.formatted(state, attackExecuting)
        );
    }
}
