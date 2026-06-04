package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Machine-readable outcome for one runtime assertion.
 */
public record NpcRuntimeAssertionResult(
        @Nonnull String assertionId,
        @Nonnull String kind,
        @Nonnull String status,
        @Nonnull String message,
        @Nullable Map<String, Object> evidence,
        @Nullable Integer firstMatchedTick,
        @Nullable Integer lastMatchedTick,
        int matchedEvidenceCount
) {
    @Nonnull
    static NpcRuntimeAssertionResult passed(@Nonnull String assertionId,
                                            @Nonnull String kind,
                                            @Nonnull String message,
                                            @Nullable Map<String, Object> evidence) {
        Integer tick = tickOrNull(evidence);
        return new NpcRuntimeAssertionResult(assertionId, kind, "passed", message, evidence, tick, tick, tick != null ? 1 : 0);
    }

    @Nonnull
    static NpcRuntimeAssertionResult passed(@Nonnull String assertionId,
                                            @Nonnull String kind,
                                            @Nonnull String message,
                                            @Nullable Map<String, Object> evidence,
                                            @Nullable Integer firstMatchedTick,
                                            @Nullable Integer lastMatchedTick,
                                            int matchedEvidenceCount) {
        return new NpcRuntimeAssertionResult(assertionId, kind, "passed", message, evidence, firstMatchedTick, lastMatchedTick, matchedEvidenceCount);
    }

    @Nonnull
    static NpcRuntimeAssertionResult failed(@Nonnull String assertionId,
                                            @Nonnull String kind,
                                            @Nonnull String message,
                                            @Nullable Map<String, Object> evidence) {
        Integer tick = tickOrNull(evidence);
        return new NpcRuntimeAssertionResult(assertionId, kind, "failed", message, evidence, tick, tick, tick != null ? 1 : 0);
    }

    @Nonnull
    static NpcRuntimeAssertionResult failed(@Nonnull String assertionId,
                                            @Nonnull String kind,
                                            @Nonnull String message,
                                            @Nullable Map<String, Object> evidence,
                                            @Nullable Integer firstMatchedTick,
                                            @Nullable Integer lastMatchedTick,
                                            int matchedEvidenceCount) {
        return new NpcRuntimeAssertionResult(assertionId, kind, "failed", message, evidence, firstMatchedTick, lastMatchedTick, matchedEvidenceCount);
    }

    @Nonnull
    static NpcRuntimeAssertionResult unknown(@Nonnull String assertionId,
                                             @Nonnull String kind,
                                             @Nonnull String message) {
        return new NpcRuntimeAssertionResult(assertionId, kind, "unknown", message, null, null, null, 0);
    }

    @Nonnull
    Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("assertionId", assertionId);
        map.put("kind", kind);
        map.put("status", status);
        map.put("message", message);
        if (evidence != null) {
            map.put("evidence", evidence);
        }
        if (firstMatchedTick != null) {
            map.put("firstMatchedTick", firstMatchedTick);
        }
        if (lastMatchedTick != null) {
            map.put("lastMatchedTick", lastMatchedTick);
        }
        if (matchedEvidenceCount > 0) {
            map.put("matchedEvidenceCount", matchedEvidenceCount);
        }
        return map;
    }

    @Nullable
    private static Integer tickOrNull(@Nullable Map<String, Object> evidence) {
        if (evidence == null) {
            return null;
        }
        Object tick = evidence.get("tick");
        return tick instanceof Number number ? number.intValue() : null;
    }
}
