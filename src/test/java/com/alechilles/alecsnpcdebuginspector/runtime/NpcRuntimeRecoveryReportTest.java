package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeRecoveryReportTest {
    @Test
    void serializesRecoveredActiveRequests() {
        NpcRuntimeRecoveryReport report = NpcRuntimeRecoveryReport.of(
                "startup",
                List.of(NpcRuntimeRecoveryReport.RecoveredRequest.of(
                        "stale",
                        "harness-recovered-stale-active-request",
                        Path.of("active", "stale.request.json"),
                        Path.of("results", "stale.result.json"),
                        Path.of("archive", "stale.request.json"),
                        "Recovered stale active request during startup"
                ))
        );

        Map<String, Object> json = NpcRuntimeJson.parseObject(report.toJson());

        assertEquals(1, ((Number) json.get("version")).intValue());
        assertEquals("startup", json.get("trigger"));
        assertEquals(1, ((Number) json.get("recoveredCount")).intValue());
        assertTrue(json.get("recoveredRequests").toString().contains("stale"));
        assertTrue(json.get("recoveredRequests").toString().contains("harness-recovered-stale-active-request"));
    }
}
