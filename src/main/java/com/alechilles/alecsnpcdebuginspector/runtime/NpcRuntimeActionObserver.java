package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return traceRecords(requestId, tick, observed, previous, new ActionLifecycleTracker());
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc observed,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull ActionLifecycleTracker tracker) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        Map<String, ActionState> previousStates = actionStates(previous);
        Map<String, ActionState> currentStates = actionStates(observed);
        addActionTransitions(records, requestId, tick, previousStates, currentStates, tracker);
        addAiActionEvidence(records, requestId, tick, observed.section("AI"), tracker);
        addCombatEvaluatorEvidence(records, requestId, tick, observed.section("Combat"), observed.section("Timers / Cooldowns"), tracker);
        return records;
    }

    private void addAiActionEvidence(@Nonnull List<NpcRuntimeTraceRecord> records,
                                     @Nonnull String requestId,
                                     int tick,
                                     @Nonnull Map<String, String> ai,
                                     @Nonnull ActionLifecycleTracker tracker) {
        addInstructionRecord(records, requestId, tick, ai, "currentTreeStep", "selected", tracker);
        addInstructionRecord(records, requestId, tick, ai, "bodyStep", "observed", tracker);
        addInstructionRecord(records, requestId, tick, ai, "headStep", "observed", tracker);
        addInstructionRecord(records, requestId, tick, ai, "queuedBodyStep", "queued", tracker);
        addInstructionRecord(records, requestId, tick, ai, "queuedHeadStep", "queued", tracker);
        addInstructionRecord(records, requestId, tick, ai, "rootInstruction", "candidate", tracker);
        addInstructionRecord(records, requestId, tick, ai, "interactionInstruction", "candidate", tracker);
        if (ai.containsKey("transitionActionsRunning")) {
            String value = ai.get("transitionActionsRunning");
            NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(requestId, tick, "action-evidence")
                    .with("actionType", "TransitionActions")
                    .with("actionId", "transitionActionsRunning")
                    .with("sourceSection", "AI")
                    .with("sourceField", "transitionActionsRunning")
                    .with("lifecycle", Boolean.parseBoolean(value) ? "running" : "not-running")
                    .with("selected", Boolean.parseBoolean(value))
                    .with("observedValue", value);
            Integer startTick = tracker.startTick("TransitionActions/transitionActionsRunning");
            if (startTick != null && Boolean.parseBoolean(value)) {
                record.with("startTick", startTick);
            }
            record.with("unsupportedFields", actionUnsupportedFields(startTick != null && Boolean.parseBoolean(value)));
            records.add(record);
        }
    }

    private void addActionTransitions(@Nonnull List<NpcRuntimeTraceRecord> records,
                                      @Nonnull String requestId,
                                      int tick,
                                      @Nonnull Map<String, ActionState> previous,
                                      @Nonnull Map<String, ActionState> current,
                                      @Nonnull ActionLifecycleTracker tracker) {
        for (ActionState currentState : current.values()) {
            ActionState previousState = previous.get(currentState.key());
            if (previousState == null || !previousState.active()) {
                tracker.start(currentState.key(), tick);
                records.add(actionTransition(requestId, tick, "action-start", currentState)
                        .with("startTick", tick));
            } else if (!previousState.observedValue().equals(currentState.observedValue())
                    || !previousState.lifecycle().equals(currentState.lifecycle())) {
                int previousStartTick = tracker.startTickOrDefault(previousState.key(), tick);
                tracker.start(currentState.key(), tick);
                records.add(actionTransition(requestId, tick, "action-change", currentState)
                        .with("previousValue", previousState.observedValue())
                        .with("previousLifecycle", previousState.lifecycle())
                        .with("previousStartTick", previousStartTick)
                        .with("previousDurationTicks", Math.max(0, tick - previousStartTick))
                        .with("currentValue", currentState.observedValue())
                        .with("currentLifecycle", currentState.lifecycle())
                        .with("startTick", tick));
            } else {
                tracker.ensureStarted(currentState.key(), tick);
            }
        }
        for (ActionState previousState : previous.values()) {
            if (!current.containsKey(previousState.key()) && previousState.active()) {
                int startTick = tracker.end(previousState.key(), tick);
                records.add(actionTransition(requestId, tick, "action-end", previousState)
                        .with("startTick", startTick)
                        .with("endTick", tick)
                        .with("durationTicks", Math.max(0, tick - startTick))
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
                                      @Nonnull String lifecycle,
                                      @Nonnull ActionLifecycleTracker tracker) {
        String value = ai.get(field);
        if (value == null || isNone(value)) {
            return;
        }
        String key = "InstructionStep/" + field;
        Integer startTick = tracker.startTick(key);
        NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(requestId, tick, "action-evidence")
                .with("actionType", "InstructionStep")
                .with("actionId", field)
                .with("sourceSection", "AI")
                .with("sourceField", field)
                .with("lifecycle", lifecycle)
                .with("selected", "selected".equals(lifecycle) || "observed".equals(lifecycle))
                .with("observedValue", value);
        if (startTick != null) {
            record.with("startTick", startTick);
        }
        record.with("unsupportedFields", actionUnsupportedFields(startTick != null));
        records.add(record);
    }

    private void addCombatEvaluatorEvidence(@Nonnull List<NpcRuntimeTraceRecord> records,
                                            @Nonnull String requestId,
                                            int tick,
                                            @Nonnull Map<String, String> combat,
                                            @Nonnull Map<String, String> timers,
                                            @Nonnull ActionLifecycleTracker tracker) {
        String executing = combat.get("executingAttack");
        if (executing == null) {
            return;
        }
        boolean selected = Boolean.parseBoolean(executing);
        String key = "CombatSupport/combatSupport.executingAttack";
        Integer startTick = tracker.startTick(key);
        Integer endTick = null;
        if (selected) {
            if (startTick == null) {
                tracker.start(key, tick);
                startTick = tick;
            }
        } else if (startTick != null) {
            endTick = tick;
            tracker.end(key, tick);
        }

        String cooldown = firstPresent(
                timers,
                "attackCooldownRemaining",
                "attackCooldown",
                "cooldownRemaining",
                "attackExecuting"
        );
        Set<String> supported = new HashSet<>();
        supported.add("candidateCombatAction");
        supported.add("eligibility");
        if (cooldown != null) {
            supported.add("cooldown");
        }
        if (selected) {
            supported.add("chosenAction");
        } else {
            supported.add("rejectionReason");
        }

        NpcRuntimeTraceRecord executingRecord = NpcRuntimeTraceRecord.of(requestId, tick, "combat-evaluator-evidence")
                .with("evaluatorType", "CombatSupport")
                .with("evaluatorId", "combatSupport.executingAttack")
                .with("candidateAction", "Attack")
                .with("lifecycle", selected ? "running" : "rejected")
                .with("selected", selected)
                .with("eligible", selected)
                .with("observedValue", executing);
        if (selected) {
            executingRecord.with("chosenAction", "Attack");
        } else {
            executingRecord.with("rejectionReason", "not-executing-attack");
        }
        if (cooldown != null) {
            executingRecord.with("cooldown", cooldown);
        }
        if (startTick != null) {
            executingRecord.with("startTick", startTick);
        }
        if (endTick != null) {
            executingRecord.with("endTick", endTick)
                    .with("durationTicks", Math.max(0, endTick - startTick));
        }
        executingRecord.with("unsupportedFields", combatUnsupportedFields(supported));
        records.add(executingRecord);

        String overrides = firstPresent(combat, "attackOverrides", "attackOverrideCount");
        if (overrides != null) {
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "combat-evaluator-evidence")
                    .with("evaluatorType", "CombatSupport")
                    .with("evaluatorId", "combatSupport.attackOverrides")
                    .with("candidateAction", "AttackOverride")
                    .with("lifecycle", "observed")
                    .with("selected", false)
                    .with("observedValue", overrides)
                    .with("unsupportedFields", combatUnsupportedFields(Set.of("candidateCombatAction"))));
        }
    }

    @Nullable
    private static String firstPresent(@Nonnull Map<String, String> values, @Nonnull String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean isNone(@Nonnull String value) {
        return value.isBlank() || "<none>".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value);
    }

    @Nonnull
    private List<String> actionUnsupportedFields(boolean hasStartTick) {
        if (!hasStartTick) {
            return ACTION_UNSUPPORTED_FIELDS;
        }
        return ACTION_UNSUPPORTED_FIELDS.stream()
                .filter(field -> !"startTick".equals(field))
                .toList();
    }

    @Nonnull
    private List<String> combatUnsupportedFields(@Nonnull Set<String> supportedFields) {
        return COMBAT_UNSUPPORTED_FIELDS.stream()
                .filter(field -> !supportedFields.contains(field))
                .toList();
    }

    public static final class ActionLifecycleTracker {
        private final Map<String, Integer> startTicks = new LinkedHashMap<>();

        int start(@Nonnull String key, int tick) {
            startTicks.put(key, tick);
            return tick;
        }

        void ensureStarted(@Nonnull String key, int tick) {
            startTicks.putIfAbsent(key, tick);
        }

        @Nullable
        Integer startTick(@Nonnull String key) {
            return startTicks.get(key);
        }

        int startTickOrDefault(@Nonnull String key, int defaultTick) {
            return startTicks.getOrDefault(key, defaultTick);
        }

        int end(@Nonnull String key, int defaultTick) {
            Integer startTick = startTicks.remove(key);
            return startTick != null ? startTick : defaultTick;
        }
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
