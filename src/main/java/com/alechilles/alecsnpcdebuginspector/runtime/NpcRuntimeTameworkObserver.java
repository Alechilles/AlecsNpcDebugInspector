package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Emits per-field Tamework evidence so runtime requests can assert mod-specific state without parsing nested snapshots.
 */
public final class NpcRuntimeTameworkObserver {
    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull Map<String, Object> tamework) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        for (Map.Entry<String, Object> section : tamework.entrySet()) {
            if (!(section.getValue() instanceof Map<?, ?> fields)) {
                continue;
            }
            for (Map.Entry<?, ?> field : fields.entrySet()) {
                if (!(field.getKey() instanceof String fieldName)) {
                    continue;
                }
                Object rawValue = field.getValue();
                records.add(NpcRuntimeTraceRecord.of(requestId, tick, "tamework-evidence")
                        .with("section", section.getKey())
                        .with("field", fieldName)
                        .with("present", rawValue != null && !isNone(String.valueOf(rawValue)))
                        .with("observedValue", rawValue != null ? String.valueOf(rawValue) : "<none>")
                        .with("unsupportedFields", List.of("fixtureMutation", "ownerMutation", "needsMutation", "commandMutation")));
            }
        }
        return records;
    }

    private boolean isNone(@Nonnull String value) {
        return value.isBlank() || "<none>".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value);
    }
}
