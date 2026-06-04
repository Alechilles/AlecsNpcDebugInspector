package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeWorldReadinessTest {
    @TempDir
    Path tempDir;

    @Test
    void readyStateSerializesForStatus() {
        NpcRuntimeWorldReadiness readiness = NpcRuntimeWorldReadiness.ready("npc_runtime_test_flatworld", 0);

        Map<String, Object> map = readiness.toMap();

        assertEquals(true, map.get("ready"));
        assertEquals("npc_runtime_test_flatworld", map.get("worldId"));
        assertEquals(true, map.get("ticking"));
        assertEquals(false, map.get("paused"));
        assertEquals(0, map.get("playerCount"));
        assertEquals("ready", map.get("reason"));
        assertFalse(readiness.shouldFailQueuedRequests());
    }

    @Test
    void startupReadinessFailuresDoNotImmediatelyFailQueuedRequests() {
        NpcRuntimeWorldReadiness readiness = NpcRuntimeWorldReadiness.notReady(
                "npc_runtime_test_flatworld",
                NpcRuntimeWorldReadiness.UNIVERSE_NOT_READY,
                "Universe startup future is not complete"
        );

        assertFalse(readiness.ready());
        assertFalse(readiness.shouldFailQueuedRequests());
        assertEquals("universe-not-ready: Universe startup future is not complete", readiness.displayReason());
    }

    @Test
    void terminalWorldFailuresFailQueuedRequests() {
        NpcRuntimeWorldReadiness readiness = NpcRuntimeWorldReadiness.fromWorld(
                "npc_runtime_test_flatworld",
                "npc_runtime_test_flatworld",
                false,
                false,
                0
        );

        assertFalse(readiness.ready());
        assertTrue(readiness.shouldFailQueuedRequests());
        assertEquals("world-not-ticking", readiness.reason());
        assertEquals(false, readiness.ticking());
    }

    @Test
    void repairsPersistedRuntimeWorldConfigWithInvalidClearWeather() throws Exception {
        Path world = tempDir.resolve("npc_runtime_test_flatworld");
        Files.createDirectories(world);
        Path config = world.resolve("config.json");
        Files.writeString(
                config,
                "{\"Version\":4.0,\"DisplayName\":\"Runtime\",\"Seed\":1.780536172895E12,"
                        + "\"WorldGen\":{\"Layers\":[{\"From\":0.0,\"To\":1.0}]},"
                        + "\"ForcedWeather\":\"clear\",\"IsTicking\":true}"
        );

        boolean repaired = NpcRuntimeFlatworldManager.repairPersistedWorldConfig(world);

        assertTrue(repaired);
        String repairedText = Files.readString(config);
        Map<String, Object> payload = NpcRuntimeJson.parseObject(repairedText);
        assertFalse(payload.containsKey("ForcedWeather"));
        assertTrue(repairedText.contains("\"Version\":4"));
        assertFalse(repairedText.contains("\"Version\":4.0"));
        assertTrue(repairedText.contains("\"Seed\":1780536172895"));
        assertFalse(repairedText.contains("1.780536172895E12"));
        assertTrue(repairedText.contains("\"From\":0"));
        assertTrue(repairedText.contains("\"To\":1"));
        assertFalse(repairedText.contains("\"From\":0.0"));
        assertFalse(repairedText.contains("\"To\":1.0"));
        assertEquals("Runtime", payload.get("DisplayName"));
        assertEquals(true, payload.get("IsTicking"));
    }

    @Test
    void repairsPersistedRuntimeWorldConfigWithFloatingVersion() throws Exception {
        Path world = tempDir.resolve("npc_runtime_test_flatworld");
        Files.createDirectories(world);
        Path config = world.resolve("config.json");
        Files.writeString(config, "{\"Version\":4.0,\"DisplayName\":\"Runtime\"}");

        boolean repaired = NpcRuntimeFlatworldManager.repairPersistedWorldConfig(world);

        assertTrue(repaired);
        String repairedText = Files.readString(config);
        Map<String, Object> payload = NpcRuntimeJson.parseObject(repairedText);
        assertTrue(repairedText.contains("\"Version\":4"));
        assertFalse(repairedText.contains("\"Version\":4.0"));
        assertEquals("Runtime", payload.get("DisplayName"));
    }

    @Test
    void leavesPersistedRuntimeWorldConfigWithoutInvalidWeatherAlone() throws Exception {
        Path world = tempDir.resolve("npc_runtime_test_flatworld");
        Files.createDirectories(world);
        Path config = world.resolve("config.json");
        Files.writeString(config, "{\"DisplayName\":\"Runtime\",\"IsTicking\":true}");

        boolean repaired = NpcRuntimeFlatworldManager.repairPersistedWorldConfig(world);

        assertFalse(repaired);
        Map<String, Object> payload = NpcRuntimeJson.parseObject(Files.readString(config));
        assertFalse(payload.containsKey("ForcedWeather"));
    }
}
