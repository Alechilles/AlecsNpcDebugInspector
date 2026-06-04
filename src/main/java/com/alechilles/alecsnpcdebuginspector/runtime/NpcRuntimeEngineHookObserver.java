package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec hooks) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        if (hooks.targetSelection()) {
            records.add(targetSelection(requestId, tick, npcId, current.section("Targeting / Sensors")));
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
                                                 @Nonnull Map<String, String> targeting) {
        if (targeting.isEmpty() || unavailable(targeting.get("status"))) {
            return NpcRuntimeTraceRecord.targetSelectionEvidence(requestId, tick, npcId, null, null, false, "unavailable: no targeting snapshot data");
        }
        String selectedSlot = null;
        for (Map.Entry<String, String> entry : targeting.entrySet()) {
            if (!entry.getKey().startsWith("target") || isNone(entry.getValue())) {
                continue;
            }
            selectedSlot = entry.getKey();
            break;
        }
        Integer candidateCount = parseInteger(firstPresent(targeting, "markedTargetSlots", "targetSlots", "slotCount"));
        if (selectedSlot == null) {
            return NpcRuntimeTraceRecord.targetSelectionEvidence(requestId, tick, npcId, null, candidateCount, false, "snapshot-target-slot");
        }
        return NpcRuntimeTraceRecord.targetSelectionEvidence(requestId, tick, npcId, selectedSlot, candidateCount, true, "snapshot-target-slot");
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
