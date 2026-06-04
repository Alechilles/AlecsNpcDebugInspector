package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Emits action and combat evidence from the inspector fields the runtime can currently observe.
 */
public final class NpcRuntimeActionObserver {
    private static final List<String> ACTION_UNSUPPORTED_FIELDS = List.of(
            "preconditions",
            "startTick",
            "endTick",
            "success",
            "failureReason",
            "cooldown",
            "targetFixtureId",
            "pathingSideEffects"
    );
    private static final List<String> COMBAT_UNSUPPORTED_FIELDS = List.of(
            "assetEvaluatorId",
            "candidateCombatAction",
            "targetFixtureId",
            "range",
            "lineOfSight",
            "cooldown",
            "eligibility",
            "resourceGates",
            "rejectionReason",
            "chosenAction"
    );

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc observed) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        addAiActionEvidence(records, requestId, tick, observed.section("AI"));
        addCombatEvaluatorEvidence(records, requestId, tick, observed.section("Combat"));
        return records;
    }

    private void addAiActionEvidence(@Nonnull List<NpcRuntimeTraceRecord> records,
                                     @Nonnull String requestId,
                                     int tick,
                                     @Nonnull Map<String, String> ai) {
        addInstructionRecord(records, requestId, tick, ai, "currentTreeStep", "selected");
        addInstructionRecord(records, requestId, tick, ai, "bodyStep", "observed");
        addInstructionRecord(records, requestId, tick, ai, "headStep", "observed");
        addInstructionRecord(records, requestId, tick, ai, "queuedBodyStep", "queued");
        addInstructionRecord(records, requestId, tick, ai, "queuedHeadStep", "queued");
        addInstructionRecord(records, requestId, tick, ai, "rootInstruction", "candidate");
        addInstructionRecord(records, requestId, tick, ai, "interactionInstruction", "candidate");
        if (ai.containsKey("transitionActionsRunning")) {
            String value = ai.get("transitionActionsRunning");
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "action-evidence")
                    .with("actionType", "TransitionActions")
                    .with("actionId", "transitionActionsRunning")
                    .with("sourceSection", "AI")
                    .with("sourceField", "transitionActionsRunning")
                    .with("lifecycle", Boolean.parseBoolean(value) ? "running" : "not-running")
                    .with("selected", Boolean.parseBoolean(value))
                    .with("observedValue", value)
                    .with("unsupportedFields", ACTION_UNSUPPORTED_FIELDS));
        }
    }

    private void addInstructionRecord(@Nonnull List<NpcRuntimeTraceRecord> records,
                                      @Nonnull String requestId,
                                      int tick,
                                      @Nonnull Map<String, String> ai,
                                      @Nonnull String field,
                                      @Nonnull String lifecycle) {
        String value = ai.get(field);
        if (value == null || isNone(value)) {
            return;
        }
        records.add(NpcRuntimeTraceRecord.of(requestId, tick, "action-evidence")
                .with("actionType", "InstructionStep")
                .with("actionId", field)
                .with("sourceSection", "AI")
                .with("sourceField", field)
                .with("lifecycle", lifecycle)
                .with("selected", "selected".equals(lifecycle) || "observed".equals(lifecycle))
                .with("observedValue", value)
                .with("unsupportedFields", ACTION_UNSUPPORTED_FIELDS));
    }

    private void addCombatEvaluatorEvidence(@Nonnull List<NpcRuntimeTraceRecord> records,
                                            @Nonnull String requestId,
                                            int tick,
                                            @Nonnull Map<String, String> combat) {
        String executing = combat.get("executingAttack");
        if (executing == null) {
            return;
        }
        boolean selected = Boolean.parseBoolean(executing);
        records.add(NpcRuntimeTraceRecord.of(requestId, tick, "combat-evaluator-evidence")
                .with("evaluatorType", "CombatSupport")
                .with("evaluatorId", "combatSupport.executingAttack")
                .with("candidateAction", "Attack")
                .with("lifecycle", selected ? "running" : "not-running")
                .with("selected", selected)
                .with("observedValue", executing)
                .with("unsupportedFields", COMBAT_UNSUPPORTED_FIELDS));
        String overrides = combat.get("attackOverrides");
        if (overrides != null) {
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "combat-evaluator-evidence")
                    .with("evaluatorType", "CombatSupport")
                    .with("evaluatorId", "combatSupport.attackOverrides")
                    .with("candidateAction", "AttackOverride")
                    .with("lifecycle", "observed")
                    .with("selected", false)
                    .with("observedValue", overrides)
                    .with("unsupportedFields", COMBAT_UNSUPPORTED_FIELDS));
        }
    }

    private boolean isNone(@Nonnull String value) {
        return value.isBlank() || "<none>".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value);
    }
}
