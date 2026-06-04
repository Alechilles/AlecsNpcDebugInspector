package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Emits explicit sensor evidence from normalized observation sections.
 */
public final class NpcRuntimeSensorObserver {
    private static final List<String> TARGET_UNSUPPORTED_FIELDS = List.of("distance", "visibility", "tags", "faction", "cooldownGate");

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc observed) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        addTargetingEvidence(records, requestId, tick, observed.section("Targeting / Sensors"));
        addBooleanSectionEvidence(records, requestId, tick, "Timer", "Timers / Cooldowns", observed.section("Timers / Cooldowns"));
        addBooleanSectionEvidence(records, requestId, tick, "Alarm", "Alarms", observed.section("Alarms"));
        addBooleanSectionEvidence(records, requestId, tick, "Flag", "Flags", observed.section("Flags"));
        return records;
    }

    private void addTargetingEvidence(@Nonnull List<NpcRuntimeTraceRecord> records,
                                      @Nonnull String requestId,
                                      int tick,
                                      @Nonnull Map<String, String> targeting) {
        for (Map.Entry<String, String> entry : targeting.entrySet()) {
            if (!entry.getKey().startsWith("target") || "targeting".equals(entry.getKey())) {
                continue;
            }
            String observedValue = entry.getValue();
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "sensor-evidence")
                    .with("sensorType", "TargetSlot")
                    .with("sensorId", entry.getKey())
                    .with("sourceSection", "Targeting / Sensors")
                    .with("sourceField", entry.getKey())
                    .with("targetSlot", entry.getKey().substring("target".length()))
                    .with("matchResult", isNone(observedValue) ? "not-matched" : "matched")
                    .with("observedValue", observedValue)
                    .with("unsupportedFields", TARGET_UNSUPPORTED_FIELDS));
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

    private boolean isNone(@Nonnull String value) {
        return value.isBlank() || "<none>".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value);
    }
}
