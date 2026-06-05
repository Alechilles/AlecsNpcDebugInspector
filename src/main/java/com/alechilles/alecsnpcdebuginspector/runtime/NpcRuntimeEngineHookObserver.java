package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Emits best-effort engine hook evidence from the live inspector fields that are available today.
 */
public final class NpcRuntimeEngineHookObserver {
    private static final Pattern VECTOR = Pattern.compile(
            "\\((-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)\\)"
    );
    private static final Pattern UUID_TEXT = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    );

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec hooks) {
        return traceRecords(requestId, tick, npcId, current, previous, hooks, new NpcRuntimeFixtureRegistry(), List.of());
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec hooks,
                                                    @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry,
                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        if (hooks.targetSelection()) {
            records.add(targetSelection(requestId, tick, npcId, current.section("Targeting / Sensors"), fixtureRegistry, fixtures));
        }
        if (hooks.pathing()) {
            records.add(pathing(requestId, tick, npcId, current.section("Pathing")));
        }
        if (hooks.combatEligibility()) {
            records.add(combatEligibility(requestId, tick, npcId, current.section("Combat")));
        }
        if (hooks.instructionLifecycle()) {
            records.add(instructionLifecycle(requestId, tick, npcId, current.section("AI"), previous != null ? previous.section("AI") : Map.of()));
        }
        return records;
    }

    @Nonnull
    private NpcRuntimeTraceRecord targetSelection(@Nonnull String requestId,
                                                 int tick,
                                                 @Nonnull String npcId,
                                                 @Nonnull Map<String, String> targeting,
                                                 @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry,
                                                 @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        if (targeting.isEmpty() || unavailable(targeting.get("status"))) {
            return NpcRuntimeTraceRecord.targetSelectionEvidence(requestId, tick, npcId, null, null, false, "unavailable: no targeting snapshot data");
        }
        String selectedSlot = null;
        String selectedValue = null;
        for (Map.Entry<String, String> entry : targeting.entrySet()) {
            if (!entry.getKey().startsWith("target") || isNone(entry.getValue())) {
                continue;
            }
            selectedSlot = entry.getKey();
            selectedValue = entry.getValue();
            break;
        }
        Integer candidateCount = parseInteger(firstPresent(targeting, "markedTargetSlots", "targetSlots", "slotCount"));
        if (selectedSlot == null) {
            return NpcRuntimeTraceRecord.targetSelectionEvidence(requestId, tick, npcId, null, candidateCount, false, "snapshot-target-slot: empty")
                    .with("selectedFixtureId", null);
        }
        TargetCorrelation correlation = correlateTarget(selectedSlot, selectedValue, fixtureRegistry, fixtures);
        NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.targetSelectionEvidence(
                requestId,
                tick,
                npcId,
                selectedSlot,
                candidateCount,
                true,
                correlation.reason()
        );
        correlation.apply(record);
        return record;
    }

    @Nonnull
    private TargetCorrelation correlateTarget(@Nonnull String selectedSlot,
                                              @Nullable String selectedValue,
                                              @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry,
                                              @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        UUID selectedUuid = parseUuid(selectedValue);
        boolean hasFixtureContext = !fixtureRegistry.fixtures().isEmpty() || !fixtures.isEmpty();
        Optional<String> selectedFixtureId = selectedUuid != null
                ? fixtureRegistry.fixtureIdForUuid(selectedUuid)
                : Optional.empty();
        if (selectedFixtureId.isEmpty()) {
            String targetSlot = selectedSlot.startsWith("target") ? selectedSlot.substring("target".length()) : selectedSlot;
            selectedFixtureId = fixtures.stream()
                    .filter(fixture -> targetSlot.equals(fixture.targetSlot()))
                    .map(NpcRuntimeFixtureSpec::fixtureId)
                    .findFirst();
        }
        NpcRuntimeFixtureSpec npcFixture = fixtureById(fixtures, "npcUnderTest");
        NpcRuntimeFixtureSpec targetFixture = selectedFixtureId.flatMap(id -> Optional.ofNullable(fixtureById(fixtures, id))).orElse(null);
        Double distance = distance(npcFixture, targetFixture);
        String reason;
        if (!hasFixtureContext) {
            reason = "snapshot-target-slot";
        } else if (selectedFixtureId.isPresent()) {
            reason = "snapshot-target-slot: fixture-correlated";
        } else {
            reason = selectedUuid != null ? "snapshot-target-slot: unknown-target-uuid" : "snapshot-target-slot: uncorrelated";
        }
        return new TargetCorrelation(
                selectedUuid != null ? selectedUuid.toString() : null,
                selectedFixtureId.orElse(null),
                position(npcFixture),
                position(targetFixture),
                distance,
                reason
        );
    }

    @Nonnull
    private NpcRuntimeTraceRecord pathing(@Nonnull String requestId,
                                          int tick,
                                          @Nonnull String npcId,
                                          @Nonnull Map<String, String> pathing) {
        if (pathing.isEmpty() || unavailable(pathing.get("status"))) {
            return NpcRuntimeTraceRecord.pathingEvidence(requestId, tick, npcId, null, List.of(), "unavailable: no pathing snapshot data", null);
        }
        List<Double> destination = parseVector(firstPresent(pathing, "lastWaypoint", "firstWaypoint"));
        String status = firstNonBlank(pathing.get("navState"), pathing.get("followingPath"), "observed");
        Boolean stuck = parseBoolean(firstPresent(pathing, "obstructed", "stuck"));
        return NpcRuntimeTraceRecord.pathingEvidence(requestId, tick, npcId, null, destination, status, stuck);
    }

    @Nonnull
    private NpcRuntimeTraceRecord combatEligibility(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull String npcId,
                                                    @Nonnull Map<String, String> combat) {
        String executing = combat.get("executingAttack");
        if (executing == null) {
            return NpcRuntimeTraceRecord.combatEligibilityEvidence(
                    requestId,
                    tick,
                    npcId,
                    null,
                    "combatSupport.executingAttack",
                    null,
                    null,
                    null,
                    null,
                    "unavailable: no combat snapshot data"
            );
        }
        boolean eligible = Boolean.parseBoolean(executing);
        return NpcRuntimeTraceRecord.combatEligibilityEvidence(
                requestId,
                tick,
                npcId,
                null,
                "combatSupport.executingAttack",
                eligible,
                null,
                null,
                null,
                "snapshot-combat-support"
        );
    }

    @Nonnull
    private NpcRuntimeTraceRecord instructionLifecycle(@Nonnull String requestId,
                                                       int tick,
                                                       @Nonnull String npcId,
                                                       @Nonnull Map<String, String> ai,
                                                       @Nonnull Map<String, String> previousAi) {
        String current = firstPresent(ai, "currentTreeStep", "bodyStep", "rootInstruction");
        if (current == null || isNone(current) || unavailable(ai.get("status"))) {
            return NpcRuntimeTraceRecord.instructionLifecycleEvidence(
                    requestId,
                    tick,
                    npcId,
                    "<unavailable>",
                    "unavailable: no instruction snapshot data",
                    null
            );
        }
        String previous = firstPresent(previousAi, "currentTreeStep", "bodyStep", "rootInstruction");
        return NpcRuntimeTraceRecord.instructionLifecycleEvidence(
                requestId,
                tick,
                npcId,
                current,
                "selected",
                previous != null && !isNone(previous) ? previous : null
        );
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
    private static List<Double> position(@Nullable NpcRuntimeFixtureSpec fixture) {
        if (fixture == null || fixture.position().size() != 3) {
            return null;
        }
        Double x = number(fixture.position().get(0));
        Double y = number(fixture.position().get(1));
        Double z = number(fixture.position().get(2));
        if (x == null || y == null || z == null) {
            return null;
        }
        return List.of(x, y, z);
    }

    @Nullable
    private static Double distance(@Nullable NpcRuntimeFixtureSpec from, @Nullable NpcRuntimeFixtureSpec to) {
        List<Double> fromPosition = position(from);
        List<Double> toPosition = position(to);
        if (fromPosition == null || toPosition == null) {
            return null;
        }
        double x = toPosition.get(0) - fromPosition.get(0);
        double y = toPosition.get(1) - fromPosition.get(1);
        double z = toPosition.get(2) - fromPosition.get(2);
        return Math.sqrt(x * x + y * y + z * z);
    }

    @Nullable
    private static Double number(@Nonnull Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private record TargetCorrelation(@Nullable String selectedTargetUuid,
                                     @Nullable String selectedFixtureId,
                                     @Nullable List<Double> npcPosition,
                                     @Nullable List<Double> targetPosition,
                                     @Nullable Double distance,
                                     @Nonnull String reason) {
        void apply(@Nonnull NpcRuntimeTraceRecord record) {
            record.with("selectedTargetUuid", selectedTargetUuid);
            record.with("selectedFixtureId", selectedFixtureId);
            if (npcPosition != null) {
                record.with("npcPosition", npcPosition);
            }
            if (targetPosition != null) {
                record.with("targetPosition", targetPosition);
            }
            if (distance != null) {
                record.with("distance", distance);
            }
        }
    }

    @Nullable
    private static String firstPresent(@Nonnull Map<String, String> values, @Nonnull String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Nonnull
    private static String firstNonBlank(@Nullable String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return "observed";
    }

    @Nullable
    private static Integer parseInteger(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static Boolean parseBoolean(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        return null;
    }

    @Nonnull
    private static List<Double> parseVector(@Nullable String value) {
        if (value == null) {
            return List.of();
        }
        Matcher matcher = VECTOR.matcher(value);
        if (!matcher.find()) {
            return List.of();
        }
        return List.of(
                Double.parseDouble(matcher.group(1)),
                Double.parseDouble(matcher.group(2)),
                Double.parseDouble(matcher.group(3))
        );
    }

    private static boolean unavailable(@Nullable String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("unavailable");
    }

    private static boolean isNone(@Nullable String value) {
        return value == null || value.isBlank() || "<none>".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value);
    }
}
