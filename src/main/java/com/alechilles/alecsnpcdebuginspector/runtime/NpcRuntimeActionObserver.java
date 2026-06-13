package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Emits action and combat evidence from the inspector fields the runtime can currently observe.
 */
public final class NpcRuntimeActionObserver {
    private static final Pattern UUID_TEXT = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    );
    private static final List<String> ACTION_UNSUPPORTED_FIELDS = List.of(
            "preconditions",
            "startTick",
            "endTick",
            "success",
            "failureReason",
            "cancelReason",
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
                                                    @Nonnull ActionLifecycleTracker tracker,
                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                                    @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry) {
        List<NpcRuntimeTraceRecord> records = traceRecords(requestId, tick, observed, previous, tracker);
        ActionTargetContext.fromObservedTarget(observed, fixtures, fixtureRegistry).apply(records);
        return records;
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
            HashSet<String> supportedFields = new HashSet<>();
            if (startTick != null && Boolean.parseBoolean(value)) {
                supportedFields.add("startTick");
            }
            applyActionOutcome(record, ai, "transitionActionsRunning", supportedFields);
            record.with("unsupportedFields", actionUnsupportedFields(supportedFields));
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
        HashSet<String> supportedFields = new HashSet<>();
        if (startTick != null) {
            supportedFields.add("startTick");
        }
        applyActionOutcome(record, ai, field, supportedFields);
        record.with("unsupportedFields", actionUnsupportedFields(supportedFields));
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
    private List<String> actionUnsupportedFields(@Nonnull Set<String> supportedFields) {
        return ACTION_UNSUPPORTED_FIELDS.stream()
                .filter(field -> !supportedFields.contains(field))
                .toList();
    }

    private void applyActionOutcome(@Nonnull NpcRuntimeTraceRecord record,
                                    @Nonnull Map<String, String> ai,
                                    @Nonnull String sourceField,
                                    @Nonnull Set<String> supportedFields) {
        Boolean success = booleanValue(firstPresent(ai, sourceField + "Success", "actionSuccess"));
        if (success != null) {
            record.with("success", success);
            supportedFields.add("success");
        }
        String failureReason = reasonValue(firstPresent(ai, sourceField + "FailureReason", "actionFailureReason"));
        if (failureReason != null) {
            record.with("failureReason", failureReason);
            supportedFields.add("failureReason");
        }
        String cancelReason = reasonValue(firstPresent(ai, sourceField + "CancelReason", "actionCancelReason"));
        if (cancelReason != null) {
            record.with("cancelReason", cancelReason);
            supportedFields.add("cancelReason");
        }
    }

    @Nullable
    private Boolean booleanValue(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return null;
    }

    @Nullable
    private String reasonValue(@Nullable String value) {
        if (value == null || isNone(value)) {
            return null;
        }
        return value;
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

    private record ActionTargetContext(@Nullable String targetFixtureId, @Nullable Double range) {
        @Nonnull
        static ActionTargetContext unavailable() {
            return new ActionTargetContext(null, null);
        }

        @Nonnull
        static ActionTargetContext fromObservedTarget(@Nonnull NpcRuntimeObservedNpc observed,
                                                      @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                                      @Nonnull NpcRuntimeFixtureRegistry registry) {
            Map<String, String> targeting = observed.section("Targeting / Sensors");
            if (targeting.isEmpty() || fixtures.isEmpty()) {
                return unavailable();
            }
            String selectedSlot = null;
            String selectedValue = null;
            for (Map.Entry<String, String> entry : targeting.entrySet()) {
                if (!entry.getKey().startsWith("target") || isNoneValue(entry.getValue())) {
                    continue;
                }
                selectedSlot = entry.getKey();
                selectedValue = entry.getValue();
                break;
            }
            if (selectedSlot == null) {
                return unavailable();
            }
            UUID selectedUuid = parseUuid(selectedValue);
            Optional<String> fixtureId = selectedUuid != null
                    ? registry.fixtureIdForUuid(selectedUuid)
                    : Optional.empty();
            if (fixtureId.isEmpty()) {
                String targetSlot = normalizeSlot(selectedSlot.startsWith("target") ? selectedSlot.substring("target".length()) : selectedSlot);
                fixtureId = fixtures.stream()
                        .filter(fixture -> targetSlot.equals(normalizeSlot(fixture.targetSlot())))
                        .map(NpcRuntimeFixtureSpec::fixtureId)
                        .findFirst();
            }
            if (fixtureId.isEmpty()) {
                return unavailable();
            }
            NpcRuntimeFixtureSpec npcFixture = fixtureById(fixtures, "npcUnderTest");
            NpcRuntimeFixtureSpec targetFixture = fixtureById(fixtures, fixtureId.get());
            return new ActionTargetContext(fixtureId.get(), distance(npcFixture, targetFixture));
        }

        void apply(@Nonnull List<NpcRuntimeTraceRecord> records) {
            if (targetFixtureId == null || targetFixtureId.isBlank()) {
                return;
            }
            for (NpcRuntimeTraceRecord record : records) {
                Object kind = record.fields().get("kind");
                if (isActionEvidenceKind(kind)) {
                    record.with("targetFixtureId", targetFixtureId);
                    removeUnsupported(record, "targetFixtureId");
                }
                if ("combat-evaluator-evidence".equals(kind) && range != null) {
                    record.with("range", range);
                    removeUnsupported(record, "range");
                }
            }
        }

        private static boolean isActionEvidenceKind(@Nullable Object kind) {
            return "action-evidence".equals(kind)
                    || "action-start".equals(kind)
                    || "action-change".equals(kind)
                    || "action-end".equals(kind)
                    || "combat-evaluator-evidence".equals(kind);
        }

        private static void removeUnsupported(@Nonnull NpcRuntimeTraceRecord record, @Nonnull String field) {
            Object unsupported = record.fields().get("unsupportedFields");
            if (!(unsupported instanceof List<?> fields)) {
                return;
            }
            List<?> filtered = fields.stream()
                    .filter(item -> !field.equals(item))
                    .toList();
            record.with("unsupportedFields", filtered);
        }

        @Nullable
        private static NpcRuntimeFixtureSpec fixtureById(@Nonnull List<NpcRuntimeFixtureSpec> fixtures, @Nonnull String fixtureId) {
            return fixtures.stream()
                    .filter(fixture -> fixtureId.equals(fixture.fixtureId()))
                    .findFirst()
                    .orElse(null);
        }

        @Nullable
        private static UUID parseUuid(@Nullable String value) {
            if (value == null) {
                return null;
            }
            Matcher matcher = UUID_TEXT.matcher(value);
            if (!matcher.find()) {
                return null;
            }
            return UUID.fromString(matcher.group(1));
        }

        @Nullable
        private static Double distance(@Nullable NpcRuntimeFixtureSpec from, @Nullable NpcRuntimeFixtureSpec to) {
            if (from == null || to == null || from.position().size() != 3 || to.position().size() != 3) {
                return null;
            }
            double sum = 0;
            for (int index = 0; index < 3; index++) {
                if (!(from.position().get(index) instanceof Number fromNumber)
                        || !(to.position().get(index) instanceof Number toNumber)) {
                    return null;
                }
                double delta = toNumber.doubleValue() - fromNumber.doubleValue();
                sum += delta * delta;
            }
            return Math.sqrt(sum);
        }

        @Nonnull
        private static String normalizeSlot(@Nullable String value) {
            return value == null ? "" : value.replaceAll("[^A-Za-z0-9]+", "").toLowerCase(java.util.Locale.ROOT);
        }

        private static boolean isNoneValue(@Nullable String value) {
            return value == null || value.isBlank() || "<none>".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value);
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
