package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Emits explicit sensor evidence from normalized observation sections.
 */
public final class NpcRuntimeSensorObserver {
    private static final List<String> TARGET_UNSUPPORTED_FIELDS = List.of(
            "targetFixtureId",
            "distance",
            "distanceBand",
            "configuredRange",
            "rangeThresholdMet",
            "visibility",
            "lineOfSight",
            "tags",
            "faction",
            "attitude",
            "cooldownGate"
    );

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc observed) {
        return traceRecords(requestId, tick, observed, List.of());
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc observed,
                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        addTargetingEvidence(records, requestId, tick, observed.section("Targeting / Sensors"), fixtures);
        addBooleanSectionEvidence(records, requestId, tick, "Timer", "Timers / Cooldowns", observed.section("Timers / Cooldowns"));
        addBooleanSectionEvidence(records, requestId, tick, "Alarm", "Alarms", observed.section("Alarms"));
        addBooleanSectionEvidence(records, requestId, tick, "Flag", "Flags", observed.section("Flags"));
        return records;
    }

    private void addTargetingEvidence(@Nonnull List<NpcRuntimeTraceRecord> records,
                                      @Nonnull String requestId,
                                      int tick,
                                      @Nonnull Map<String, String> targeting,
                                      @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        for (Map.Entry<String, String> entry : targeting.entrySet()) {
            if (!entry.getKey().startsWith("target") || "targeting".equals(entry.getKey())) {
                continue;
            }
            String observedValue = entry.getValue();
            String targetSlot = entry.getKey().substring("target".length());
            ArrayList<String> unsupportedFields = new ArrayList<>(TARGET_UNSUPPORTED_FIELDS);
            NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(requestId, tick, "sensor-evidence")
                    .with("sensorType", "TargetSlot")
                    .with("sensorId", entry.getKey())
                    .with("sourceSection", "Targeting / Sensors")
                    .with("sourceField", entry.getKey())
                    .with("targetSlot", targetSlot)
                    .with("matchResult", isNone(observedValue) ? "not-matched" : "matched")
                    .with("observedValue", observedValue);
            enrichTargetFixtureEvidence(record, targetSlot, fixtures, unsupportedFields);
            record.with("unsupportedFields", unsupportedFields);
            records.add(record);
        }
        if (targeting.containsKey("sensorScopeKeys")) {
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "sensor-evidence")
                    .with("sensorType", "SensorScope")
                    .with("sensorId", "sensorScopeKeys")
                    .with("sourceSection", "Targeting / Sensors")
                    .with("sourceField", "sensorScopeKeys")
                    .with("matchResult", "observed")
                    .with("observedValue", targeting.get("sensorScopeKeys"))
                    .with("unsupportedFields", List.of("sensorType", "sensorInput", "predicateResult")));
        }
    }

    private void addBooleanSectionEvidence(@Nonnull List<NpcRuntimeTraceRecord> records,
                                           @Nonnull String requestId,
                                           int tick,
                                           @Nonnull String sensorType,
                                           @Nonnull String sectionName,
                                           @Nonnull Map<String, String> fields) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = entry.getValue();
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                continue;
            }
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "sensor-evidence")
                    .with("sensorType", sensorType)
                    .with("sensorId", entry.getKey())
                    .with("sourceSection", sectionName)
                    .with("sourceField", entry.getKey())
                    .with("matchResult", Boolean.parseBoolean(value) ? "matched" : "not-matched")
                    .with("observedValue", value)
                    .with("unsupportedFields", List.of("configuredThreshold", "cooldownGate")));
        }
    }

    private void enrichTargetFixtureEvidence(@Nonnull NpcRuntimeTraceRecord record,
                                             @Nonnull String targetSlot,
                                             @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                             @Nonnull List<String> unsupportedFields) {
        NpcRuntimeFixtureSpec npcUnderTest = null;
        ArrayList<NpcRuntimeFixtureSpec> matchingTargets = new ArrayList<>();
        for (NpcRuntimeFixtureSpec fixture : fixtures) {
            if (fixture.kind() == NpcRuntimeFixtureKind.NPC_UNDER_TEST) {
                npcUnderTest = fixture;
            }
            if (targetSlot.equals(fixture.targetSlot())) {
                matchingTargets.add(fixture);
            }
        }
        if (matchingTargets.size() != 1) {
            return;
        }
        NpcRuntimeFixtureSpec target = matchingTargets.getFirst();
        record.with("targetFixtureId", target.fixtureId());
        unsupportedFields.remove("targetFixtureId");
        Double distance = distance(npcUnderTest, target);
        if (distance != null) {
            record.with("distance", distance);
            record.with("distanceBand", distanceBand(distance));
            unsupportedFields.remove("distance");
            unsupportedFields.remove("distanceBand");
            if (target.radius() != null) {
                record.with("configuredRange", target.radius());
                record.with("rangeThresholdMet", distance <= target.radius());
                unsupportedFields.remove("configuredRange");
                unsupportedFields.remove("rangeThresholdMet");
            }
        }
        record.with("visibility", target.visible());
        record.with("lineOfSight", target.visible());
        unsupportedFields.remove("visibility");
        unsupportedFields.remove("lineOfSight");
        if (!target.tags().isEmpty()) {
            record.with("tags", target.tags());
            unsupportedFields.remove("tags");
        }
        if (target.faction() != null) {
            record.with("faction", target.faction());
            unsupportedFields.remove("faction");
        }
        if (target.attitude() != null) {
            record.with("attitude", target.attitude());
            unsupportedFields.remove("attitude");
        }
    }

    @Nullable
    private Double distance(@Nullable NpcRuntimeFixtureSpec from, @Nonnull NpcRuntimeFixtureSpec to) {
        if (from == null || from.position().size() != 3 || to.position().size() != 3) {
            return null;
        }
        Double fromX = number(from.position().get(0));
        Double fromY = number(from.position().get(1));
        Double fromZ = number(from.position().get(2));
        Double toX = number(to.position().get(0));
        Double toY = number(to.position().get(1));
        Double toZ = number(to.position().get(2));
        if (fromX == null || fromY == null || fromZ == null || toX == null || toY == null || toZ == null) {
            return null;
        }
        double x = toX - fromX;
        double y = toY - fromY;
        double z = toZ - fromZ;
        return Math.sqrt(x * x + y * y + z * z);
    }

    @Nullable
    private Double number(@Nonnull Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    @Nonnull
    private String distanceBand(double distance) {
        if (distance <= 4) {
            return "near";
        }
        if (distance <= 12) {
            return "medium";
        }
        return "far";
    }

    private boolean isNone(@Nonnull String value) {
        return value.isBlank() || "<none>".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value);
    }
}
