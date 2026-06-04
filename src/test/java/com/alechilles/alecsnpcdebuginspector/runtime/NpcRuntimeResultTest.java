package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeResultTest {
    @Test
    void cleanupFailedResultCarriesStructuredCleanupReport() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));
        NpcRuntimeRequest request = NpcRuntimeRequest.parse(
                "{\"version\":1,\"requestId\":\"cleanup_bad\",\"assetId\":\"Asset\",\"roleId\":\"Role\",\"ticks\":5}",
                config
        );
        NpcRuntimeCleanupReport cleanupReport = NpcRuntimeCleanupReport.builder()
                .entityRemoval(false)
                .unresolvedFixture("npcUnderTest")
                .build();

        NpcRuntimeResult result = NpcRuntimeResult.cleanupFailed(
                request,
                5,
                Path.of("cleanup_bad.trace.jsonl"),
                NpcRuntimeResult.Summary.empty(),
                cleanupReport
        );

        Map<String, Object> json = NpcRuntimeJson.parseObject(result.toJson());
        assertEquals("failed", json.get("status"));
        assertEquals("cleanup-failed", json.get("classification"));
        assertTrue(json.get("error").toString().contains("cleanup failed"));
        assertTrue(json.get("cleanup").toString().contains("unresolvedFixtureIds"));
    }
}
