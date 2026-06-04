package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One stable JSONL trace event.
 */
public final class NpcRuntimeTraceRecord {
    private final LinkedHashMap<String, Object> fields = new LinkedHashMap<>();

    private NpcRuntimeTraceRecord(@Nonnull String requestId, int tick, @Nonnull String kind) {
        fields.put("version", 1);
        fields.put("requestId", requestId);
        fields.put("tick", tick);
        fields.put("kind", kind);
    }

    @Nonnull
    public static NpcRuntimeTraceRecord of(@Nonnull String requestId, int tick, @Nonnull String kind) {
        return new NpcRuntimeTraceRecord(requestId, tick, kind);
    }

    @Nonnull
    public static NpcRuntimeTraceRecord tameworkFixtureMutation(@Nonnull String requestId,
                                                                int tick,
                                                                @Nonnull String fixtureId,
                                                                @Nonnull String field,
                                                                @Nullable Object requestedValue,
                                                                @Nullable Object appliedValue,
                                                                @Nonnull String status,
                                                                @Nonnull List<?> unsupportedFields) {
        return of(requestId, tick, "tamework-fixture-mutation")
                .with("fixtureId", fixtureId)
                .with("field", field)
                .with("requestedValue", requestedValue)
                .with("appliedValue", appliedValue)
                .with("status", status)
                .with("unsupportedFields", unsupportedFields);
    }

    @Nonnull
    public static NpcRuntimeTraceRecord targetSelectionEvidence(@Nonnull String requestId,
                                                                int tick,
                                                                @Nonnull String npcId,
                                                                @Nullable String targetId,
                                                                @Nullable Integer candidateCount,
                                                                @Nullable Boolean selected,
                                                                @Nonnull String reason) {
        NpcRuntimeTraceRecord record = of(requestId, tick, "target-selection-evidence")
                .with("npcId", npcId)
                .with("reason", reason);
        if (targetId != null) {
            record.with("targetId", targetId);
        }
        if (candidateCount != null) {
            record.with("candidateCount", candidateCount);
        }
        if (selected != null) {
            record.with("selected", selected);
        }
        return record;
    }

    @Nonnull
    public static NpcRuntimeTraceRecord pathingEvidence(@Nonnull String requestId,
                                                        int tick,
                                                        @Nonnull String npcId,
                                                        @Nullable String targetId,
                                                        @Nonnull List<?> destination,
                                                        @Nonnull String status,
                                                        @Nullable Boolean stuck) {
        NpcRuntimeTraceRecord record = of(requestId, tick, "pathing-evidence")
                .with("npcId", npcId)
                .with("destination", destination)
                .with("status", status);
        if (targetId != null) {
            record.with("targetId", targetId);
        }
        if (stuck != null) {
            record.with("stuck", stuck);
        }
        return record;
    }

    @Nonnull
    public static NpcRuntimeTraceRecord combatEligibilityEvidence(@Nonnull String requestId,
                                                                  int tick,
                                                                  @Nonnull String npcId,
                                                                  @Nullable String targetId,
                                                                  @Nonnull String actionId,
                                                                  @Nullable Boolean eligible,
                                                                  @Nullable Boolean rangeOk,
                                                                  @Nullable Boolean cooldownOk,
                                                                  @Nullable Boolean lineOfSightOk,
                                                                  @Nonnull String reason) {
        NpcRuntimeTraceRecord record = of(requestId, tick, "combat-eligibility-evidence")
                .with("npcId", npcId)
                .with("actionId", actionId)
                .with("reason", reason);
        if (targetId != null) {
            record.with("targetId", targetId);
        }
        if (eligible != null) {
            record.with("eligible", eligible);
        }
        if (rangeOk != null) {
            record.with("rangeOk", rangeOk);
        }
        if (cooldownOk != null) {
            record.with("cooldownOk", cooldownOk);
        }
        if (lineOfSightOk != null) {
            record.with("lineOfSightOk", lineOfSightOk);
        }
        return record;
    }

    @Nonnull
    public static NpcRuntimeTraceRecord instructionLifecycleEvidence(@Nonnull String requestId,
                                                                     int tick,
                                                                     @Nonnull String npcId,
                                                                     @Nonnull String instructionId,
                                                                     @Nonnull String status,
                                                                     @Nullable String previousStatus) {
        NpcRuntimeTraceRecord record = of(requestId, tick, "instruction-lifecycle-evidence")
                .with("npcId", npcId)
                .with("instructionId", instructionId)
                .with("status", status);
        if (previousStatus != null) {
            record.with("previousStatus", previousStatus);
        }
        return record;
    }

    @Nonnull
    public NpcRuntimeTraceRecord with(@Nonnull String key, @Nullable Object value) {
        if ("version".equals(key) || "requestId".equals(key) || "tick".equals(key) || "kind".equals(key)) {
            throw new IllegalArgumentException("Trace field '" + key + "' is reserved");
        }
        fields.put(key, value);
        return this;
    }

    @Nonnull
    public Map<String, Object> fields() {
        return Collections.unmodifiableMap(fields);
    }

    @Nonnull
    public String toJson() {
        return NpcRuntimeJson.stringify(fields);
    }
}
