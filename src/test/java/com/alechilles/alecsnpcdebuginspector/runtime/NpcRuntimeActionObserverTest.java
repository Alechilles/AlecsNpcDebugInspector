package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

                        === Timers / Cooldowns ===
                        - Attack Cooldown Remaining: 12
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
        assertEquals(true, combat.fields().get("eligible"));
        assertEquals("Attack", combat.fields().get("chosenAction"));
        assertEquals("12", combat.fields().get("cooldown"));
        assertFalse(combat.fields().get("unsupportedFields").toString().contains("eligibility"));
        assertTrue(records.stream().anyMatch(record ->
                "combat-evaluator-evidence".equals(record.fields().get("kind"))
                        && "combatSupport.attackOverrides".equals(record.fields().get("evaluatorId"))
                        && "observed".equals(record.fields().get("lifecycle"))));
    }

    @Test
    void emitsRejectedCombatEvaluatorEvidenceWhenAttackIsNotExecuting() {
        NpcRuntimeObservedNpc observed = NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Combat ===
                        - Executing Attack: false

                        === Timers / Cooldowns ===
                        - Attack Cooldown Remaining: 0
                        """
        ));

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeActionObserver().traceRecords("request-a", 4, observed);

        NpcRuntimeTraceRecord rejected = records.stream()
                .filter(record -> "combat-evaluator-evidence".equals(record.fields().get("kind")))
                .filter(record -> "combatSupport.executingAttack".equals(record.fields().get("evaluatorId")))
                .findFirst()
                .orElseThrow();
        assertEquals("rejected", rejected.fields().get("lifecycle"));
        assertEquals(false, rejected.fields().get("selected"));
        assertEquals(false, rejected.fields().get("eligible"));
        assertEquals("not-executing-attack", rejected.fields().get("rejectionReason"));
        assertEquals("0", rejected.fields().get("cooldown"));
        assertTrue(rejected.fields().get("unsupportedFields").toString().contains("range"));
    }

    @Test
    void tracksCombatEvaluatorDurationWhenAttackStopsExecuting() {
        NpcRuntimeActionObserver observer = new NpcRuntimeActionObserver();
        NpcRuntimeActionObserver.ActionLifecycleTracker tracker = new NpcRuntimeActionObserver.ActionLifecycleTracker();
        NpcRuntimeObservedNpc running = NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Combat ===
                        - Executing Attack: true
                        """
        ));
        NpcRuntimeObservedNpc stopped = NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Combat ===
                        - Executing Attack: false
                        """
        ));

        observer.traceRecords("request-a", 20, running, null, tracker);
        List<NpcRuntimeTraceRecord> stoppedRecords = observer.traceRecords("request-a", 35, stopped, running, tracker);

        NpcRuntimeTraceRecord stoppedEvidence = stoppedRecords.stream()
                .filter(record -> "combat-evaluator-evidence".equals(record.fields().get("kind")))
                .filter(record -> "combatSupport.executingAttack".equals(record.fields().get("evaluatorId")))
                .findFirst()
                .orElseThrow();
        assertEquals("rejected", stoppedEvidence.fields().get("lifecycle"));
        assertEquals(20, stoppedEvidence.fields().get("startTick"));
        assertEquals(35, stoppedEvidence.fields().get("endTick"));
        assertEquals(15, stoppedEvidence.fields().get("durationTicks"));
    }

    @Test
    void emitsActionStartChangeAndEndTransitionsFromAdjacentSnapshots() {
        NpcRuntimeActionObserver observer = new NpcRuntimeActionObserver();
        NpcRuntimeObservedNpc previous = observedWithAi("""
                === AI ===
                - Current Tree Step: Sequence[Patrol]
                - Current Body Step: WalkToPoint
                - Transition Actions Running: false
                """);
        NpcRuntimeObservedNpc current = observedWithAi("""
                === AI ===
                - Current Tree Step: Sequence[Attack]
                - Current Body Step: MoveToTarget
                - Transition Actions Running: true
                """);
        NpcRuntimeObservedNpc ended = observedWithAi("""
                === AI ===
                - Current Tree Step: <none>
                - Current Body Step: <none>
                - Transition Actions Running: false
                """);

        List<NpcRuntimeTraceRecord> initialRecords = observer.traceRecords("request-a", 1, previous, null);
        List<NpcRuntimeTraceRecord> changedRecords = observer.traceRecords("request-a", 2, current, previous);
        List<NpcRuntimeTraceRecord> endedRecords = observer.traceRecords("request-a", 3, ended, current);

        NpcRuntimeTraceRecord start = initialRecords.stream()
                .filter(record -> "action-start".equals(record.fields().get("kind")))
                .filter(record -> "currentTreeStep".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals("Sequence[Patrol]", start.fields().get("currentValue"));
        assertEquals(1, start.fields().get("startTick"));

        NpcRuntimeTraceRecord change = changedRecords.stream()
                .filter(record -> "action-change".equals(record.fields().get("kind")))
                .filter(record -> "currentTreeStep".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals("Sequence[Patrol]", change.fields().get("previousValue"));
        assertEquals("Sequence[Attack]", change.fields().get("currentValue"));

        NpcRuntimeTraceRecord transitionStart = changedRecords.stream()
                .filter(record -> "action-start".equals(record.fields().get("kind")))
                .filter(record -> "transitionActionsRunning".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals(true, transitionStart.fields().get("selected"));

        NpcRuntimeTraceRecord end = endedRecords.stream()
                .filter(record -> "action-end".equals(record.fields().get("kind")))
                .filter(record -> "currentTreeStep".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals("Sequence[Attack]", end.fields().get("previousValue"));
        assertEquals(3, end.fields().get("endTick"));
    }

    @Test
    void tracksActionStartAndDurationAcrossSampledObservations() {
        NpcRuntimeActionObserver observer = new NpcRuntimeActionObserver();
        NpcRuntimeActionObserver.ActionLifecycleTracker tracker = new NpcRuntimeActionObserver.ActionLifecycleTracker();
        NpcRuntimeObservedNpc started = observedWithAi("""
                === AI ===
                - Current Tree Step: Sequence[Attack]
                - Current Body Step: MoveToTarget
                - Transition Actions Running: true
                """);
        NpcRuntimeObservedNpc sustained = observedWithAi("""
                === AI ===
                - Current Tree Step: Sequence[Attack]
                - Current Body Step: MoveToTarget
                - Transition Actions Running: true
                """);
        NpcRuntimeObservedNpc ended = observedWithAi("""
                === AI ===
                - Current Tree Step: <none>
                - Current Body Step: <none>
                - Transition Actions Running: false
                """);

        observer.traceRecords("request-a", 10, started, null, tracker);
        List<NpcRuntimeTraceRecord> sustainedRecords = observer.traceRecords("request-a", 25, sustained, started, tracker);
        List<NpcRuntimeTraceRecord> endedRecords = observer.traceRecords("request-a", 40, ended, sustained, tracker);

        NpcRuntimeTraceRecord sustainedEvidence = sustainedRecords.stream()
                .filter(record -> "action-evidence".equals(record.fields().get("kind")))
                .filter(record -> "currentTreeStep".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals(10, sustainedEvidence.fields().get("startTick"));
        assertTrue(sustainedRecords.stream().noneMatch(record -> "action-start".equals(record.fields().get("kind"))));

        NpcRuntimeTraceRecord end = endedRecords.stream()
                .filter(record -> "action-end".equals(record.fields().get("kind")))
                .filter(record -> "currentTreeStep".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals(10, end.fields().get("startTick"));
        assertEquals(40, end.fields().get("endTick"));
        assertEquals(30, end.fields().get("durationTicks"));
    }

    @Test
    void resetsLifecycleTimingWhenSelectedActionChanges() {
        NpcRuntimeActionObserver observer = new NpcRuntimeActionObserver();
        NpcRuntimeActionObserver.ActionLifecycleTracker tracker = new NpcRuntimeActionObserver.ActionLifecycleTracker();
        NpcRuntimeObservedNpc previous = observedWithAi("""
                === AI ===
                - Current Tree Step: Sequence[Patrol]
                - Current Body Step: WalkToPoint
                """);
        NpcRuntimeObservedNpc current = observedWithAi("""
                === AI ===
                - Current Tree Step: Sequence[Attack]
                - Current Body Step: MoveToTarget
                """);

        observer.traceRecords("request-a", 5, previous, null, tracker);
        List<NpcRuntimeTraceRecord> changedRecords = observer.traceRecords("request-a", 12, current, previous, tracker);

        NpcRuntimeTraceRecord change = changedRecords.stream()
                .filter(record -> "action-change".equals(record.fields().get("kind")))
                .filter(record -> "currentTreeStep".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals(5, change.fields().get("previousStartTick"));
        assertEquals(7, change.fields().get("previousDurationTicks"));
        assertEquals(12, change.fields().get("startTick"));

        NpcRuntimeTraceRecord currentEvidence = changedRecords.stream()
                .filter(record -> "action-evidence".equals(record.fields().get("kind")))
                .filter(record -> "currentTreeStep".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals(12, currentEvidence.fields().get("startTick"));
    }

    @Test
    void includesActionOutcomeFieldsWhenSnapshotExposesThem() {
        NpcRuntimeObservedNpc observed = observedWithAi("""
                === AI ===
                - Current Tree Step: Sequence[Attack]
                - Action Success: false
                - Action Failure Reason: path-obstructed
                - Action Cancel Reason: scenario-canceled
                """);

        List<NpcRuntimeTraceRecord> records = new NpcRuntimeActionObserver().traceRecords("request-a", 6, observed);

        NpcRuntimeTraceRecord action = records.stream()
                .filter(record -> "action-evidence".equals(record.fields().get("kind")))
                .filter(record -> "currentTreeStep".equals(record.fields().get("actionId")))
                .findFirst()
                .orElseThrow();
        assertEquals(false, action.fields().get("success"));
        assertEquals("path-obstructed", action.fields().get("failureReason"));
        assertEquals("scenario-canceled", action.fields().get("cancelReason"));
        assertFalse(action.fields().get("unsupportedFields").toString().contains("success"));
        assertFalse(action.fields().get("unsupportedFields").toString().contains("failureReason"));
        assertFalse(action.fields().get("unsupportedFields").toString().contains("cancelReason"));
    }

    private static NpcRuntimeObservedNpc observedWithAi(String details) {
        return NpcRuntimeObservedNpc.fromSnapshot(null, new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                details
        ));
    }
}
