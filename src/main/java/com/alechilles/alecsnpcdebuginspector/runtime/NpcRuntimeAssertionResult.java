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
        @Nullable Map<String, Object> evidence
) {
    @Nonnull
    static NpcRuntimeAssertionResult passed(@Nonnull String assertionId,
                                            @Nonnull String kind,
                                            @Nonnull String message,
                                            @Nullable Map<String, Object> evidence) {
        return new NpcRuntimeAssertionResult(assertionId, kind, "passed", message, evidence);
    }

    @Nonnull
    static NpcRuntimeAssertionResult failed(@Nonnull String assertionId,
                                            @Nonnull String kind,
                                            @Nonnull String message,
                                            @Nullable Map<String, Object> evidence) {
        return new NpcRuntimeAssertionResult(assertionId, kind, "failed", message, evidence);
    }

    @Nonnull
    static NpcRuntimeAssertionResult unknown(@Nonnull String assertionId,
                                             @Nonnull String kind,
                                             @Nonnull String message) {
        return new NpcRuntimeAssertionResult(assertionId, kind, "unknown", message, null);
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
        return map;
    }
}
