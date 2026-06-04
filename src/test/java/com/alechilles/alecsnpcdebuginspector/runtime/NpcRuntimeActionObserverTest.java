package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeActionObserverTest {
    @Test
    void emitsActionAndCombatEvaluatorEvidenceFromObservedFields() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === AI ===
                        - Current Tree Step: Sequence[Attack]
                        - Current Body Step: MoveToTarget
                        - Queued Body Step: <none>
                        - Transition Actions Running: true
                        - Root Instruction: [0]Instruction

                        === Combat ===
                        - Executing Attack: true
                        - Attack Override Count: 1
                        """
        ));

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeActionObserver().traceRecords("request-a", 2, observed);

        NpcRuntimeTraceRecord action = records.stream()
                .filter(record -> "action-evidence".equals(record.fields().get("kind")))
                .filter(record -> "currentTreeStep".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals("InstructionStep", action.fields().get("actionType"));
        assertEquals("selected", action.fields().get("lifecycle"));
        assertEquals(true, action.fields().get("selected"));
        assertTrue(records.stream().anyMatch(record ->
                "action-evidence".equals(record.fields().get("kind"))
                        && "transitionActionsRunning".equals(record.fields().get("actionId"))));
        assertTrue(records.stream().anyMatch(record ->
                "action-evidence".equals(record.fields().get("kind"))
                        && "rootInstruction".equals(record.fields().get("actionId"))
                        && "candidate".equals(record.fields().get("lifecycle"))));

        NpcRuntimeTraceRecord combat = records.stream()
                .filter(record -> "combat-evaluator-evidence".equals(record.fields().get("kind")))
                .filter(record -> "combatSupport.executingAttack".equals(record.fields().get("evaluatorId")))
                .findFirst()
                .orElseThrow();
        assertEquals("CombatSupport", combat.fields().get("evaluatorType"));
        assertEquals("running", combat.fields().get("lifecycle"));
        assertEquals(true, combat.fields().get("selected"));
        assertTrue(combat.toJson().contains("eligibility"));
    }
}
