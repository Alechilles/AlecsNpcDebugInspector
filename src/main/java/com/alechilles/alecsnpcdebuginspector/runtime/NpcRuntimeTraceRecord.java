package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.LinkedHashMap;
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
    public NpcRuntimeTraceRecord with(@Nonnull String key, @Nullable Object value) {
        fields.put(key, value);
        return this;
    }

    @Nonnull
    public Map<String, Object> fields() {
        return Map.copyOf(fields);
    }

    @Nonnull
    public String toJson() {
        return NpcRuntimeJson.stringify(fields);
    }
}
