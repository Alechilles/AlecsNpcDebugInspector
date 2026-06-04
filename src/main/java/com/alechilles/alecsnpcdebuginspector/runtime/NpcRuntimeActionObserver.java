package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
        return traceRecords(requestId, tick, observed, null);
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc observed,
                                                    @Nullable NpcRuntimeObservedNpc previous) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        addAiActionEvidence(records, requestId, tick, observed.section("AI"));
        addActionTransitions(records, requestId, tick, actionStates(previous), actionStates(observed));
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

    private void addActionTransitions(@Nonnull List<NpcRuntimeTraceRecord> records,
                                      @Nonnull String requestId,
                                      int tick,
                                      @Nonnull Map<String, ActionState> previous,
                                      @Nonnull Map<String, ActionState> current) {
        for (ActionState currentState : current.values()) {
            ActionState previousState = previous.get(currentState.key());
            if (previousState == null || !previousState.active()) {
                records.add(actionTransition(requestId, tick, "action-start", currentState)
                        .with("startTick", tick));
            } else if (!previousState.observedValue().equals(currentState.observedValue())
                    || !previousState.lifecycle().equals(currentState.lifecycle())) {
                records.add(actionTransition(requestId, tick, "action-change", currentState)
                        .with("previousValue", previousState.observedValue())
                        .with("previousLifecycle", previousState.lifecycle())
                        .with("currentValue", currentState.observedValue())
                        .with("currentLifecycle", currentState.lifecycle()));
            }
        }
        for (ActionState previousState : previous.values()) {
            if (!current.containsKey(previousState.key()) && previousState.active()) {
                records.add(actionTransition(requestId, tick, "action-end", previousState)
                        .with("endTick", tick)
                        .with("previousValue", previousState.observedValue())
                        .with("previousLifecycle", previousState.lifecycle()));
            }
        }
    }

    @Nonnull
    private NpcRuntimeTraceRecord actionTransition(@Nonnull String requestId,
                                                  int tick,
                                                  @Nonnull String kind,
                                                  @Nonnull ActionState state) {
        return NpcRuntimeTraceRecord.of(requestId, tick, kind)
                .with("actionType", state.actionType())
                .with("actionId", state.actionId())
                .with("sourceSection", "AI")
                .with("sourceField", state.actionId())
                .with("lifecycle", state.lifecycle())
                .with("selected", state.selected())
                .with("observedValue", state.observedValue())
                .with("currentValue", state.observedValue());
    }

    @Nonnull
    private Map<String, ActionState> actionStates(@Nullable NpcRuntimeObservedNpc observed) {
        if (observed == null) {
            return Map.of();
        }
        LinkedHashMap<String, ActionState> states = new LinkedHashMap<>();
        Map<String, String> ai = observed.section("AI");
        addInstructionState(states, ai, "currentTreeStep", "selected");
        addInstructionState(states, ai, "bodyStep", "observed");
        addInstructionState(states, ai, "headStep", "observed");
        addInstructionState(states, ai, "queuedBodyStep", "queued");
        addInstructionState(states, ai, "queuedHeadStep", "queued");
        addInstructionState(states, ai, "rootInstruction", "candidate");
        addInstructionState(states, ai, "interactionInstruction", "candidate");
        String transitionActions = ai.get("transitionActionsRunning");
        if (transitionActions != null && Boolean.parseBoolean(transitionActions)) {
            ActionState state = new ActionState(
                    "TransitionActions",
                    "transitionActionsRunning",
                    "running",
                    true,
                    transitionActions
            );
            states.put(state.key(), state);
        }
        return states;
    }

    private void addInstructionState(@Nonnull Map<String, ActionState> states,
                                     @Nonnull Map<String, String> ai,
                                     @Nonnull String field,
                                     @Nonnull String lifecycle) {
        String value = ai.get(field);
        if (value == null || isNone(value)) {
            return;
        }
        boolean selected = "selected".equals(lifecycle) || "observed".equals(lifecycle);
        ActionState state = new ActionState("InstructionStep", field, lifecycle, selected, value);
        states.put(state.key(), state);
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

    private record ActionState(@Nonnull String actionType,
                               @Nonnull String actionId,
                               @Nonnull String lifecycle,
                               boolean selected,
                               @Nonnull String observedValue) {
        @Nonnull
        String key() {
            return actionType + "/" + actionId;
        }

        boolean active() {
            return selected || "running".equals(lifecycle);
        }
    }
}
