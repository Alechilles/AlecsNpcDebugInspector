package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeHarnessStatusTest {
    @TempDir
    Path tempDir;

    @Test
    void serializesHeadlessStatusContract() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessStatus status = NpcRuntimeHarnessStatus.fromService(
                config,
                true,
                "active-request",
                2,
                new NpcRuntimeHarnessStatus.LastResult("last-request", "passed", "passed"),
                new NpcRuntimeHarnessStatus.WorldSnapshot(true, "npc_runtime_test_flatworld", true, 0),
                Instant.parse("2026-06-03T23:38:29.358562Z")
        );

        Map<String, Object> json = NpcRuntimeJson.parseObject(status.toJson());

        assertEquals(1, ((Number) json.get("version")).intValue());
        assertEquals(true, json.get("serverReady"));
        assertEquals(true, json.get("harnessEnabled"));
        assertEquals(true, json.get("worldReady"));
        assertEquals("npc_runtime_test_flatworld", json.get("worldId"));
        assertEquals(true, json.get("worldTicking"));
        assertEquals(0, ((Number) json.get("playerCount")).intValue());
        assertEquals("active-request", json.get("activeRequestId"));
        assertEquals(2, ((Number) json.get("queuedCount")).intValue());
        assertEquals("2026-06-03T23:38:29.358562Z", json.get("updatedAt"));

        Object lastResult = json.get("lastResult");
        assertNotNull(lastResult);
        assertTrue(lastResult.toString().contains("last-request"));
        assertTrue(json.get("paths").toString().contains(config.paths().requests().toString()));
    }

    @Test
    void writerAtomicallyPublishesStatusFile() throws Exception {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(tempDir);
        NpcRuntimeHarnessStatusWriter writer = new NpcRuntimeHarnessStatusWriter(config.paths());
        NpcRuntimeHarnessStatus status = NpcRuntimeHarnessStatus.fromService(
                config,
                false,
                null,
                0,
                null,
                NpcRuntimeHarnessStatus.WorldSnapshot.notReady(config.defaultWorldId()),
                Instant.parse("2026-06-03T23:38:29Z")
        );

        writer.write(status);

        assertTrue(Files.exists(config.paths().statusFile()));
        assertFalse(Files.exists(config.paths().status().resolve("harness-status.json.tmp")));
        Map<String, Object> json = NpcRuntimeJson.parseObject(Files.readString(config.paths().statusFile()));
        assertEquals(false, json.get("harnessEnabled"));
        assertEquals(false, json.get("worldReady"));
        assertEquals("npc_runtime_test_flatworld", json.get("worldId"));
    }
}
