package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeTameworkObserverTest {
    @Test
    void emitsPerFieldTameworkEvidence() {
        List<NpcRuntimeTraceRecord> records = new NpcRuntimeTameworkObserver().traceRecords(
                "request-a",
                2,
                Map.of(
                        "tamework", Map.of("pluginLoaded", "true", "tamedComponent", "false"),
                        "tameworkDiagnostics", Map.of("healthStatus", "HEALTHY")
                )
        );

        NpcRuntimeTraceRecord pluginLoaded = records.stream()
                .filter(record -> "pluginLoaded".equals(record.fields().get("field")))
                .findFirst()
                .orElseThrow();

        assertEquals("tamework-evidence", pluginLoaded.fields().get("kind"));
        assertEquals("tamework", pluginLoaded.fields().get("section"));
        assertEquals("true", pluginLoaded.fields().get("observedValue"));
        assertEquals(true, pluginLoaded.fields().get("present"));
        assertTrue(pluginLoaded.toJson().contains("fixtureMutation"));
    }
}
