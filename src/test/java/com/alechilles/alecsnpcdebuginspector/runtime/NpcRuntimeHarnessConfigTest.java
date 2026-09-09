package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeHarnessConfigTest {
    @Test
    void defaultsAreSafeAndResolveHarnessDirectories() {
        Path root = Path.of("build", "test-userdata");

        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(root);

        assertFalse(config.enabledByDefault());
        assertFalse(config.autoEnable());
        assertFalse(config.initiallyEnabled());
        assertEquals(1200, config.maxTicks());
        assertEquals(64, config.maxEntities());
        assertEquals(104_857_600L, config.maxTraceBytes());
        assertEquals("npc_runtime_test_flatworld", config.instanceId());
        assertEquals("npc_runtime_test_flatworld", config.defaultWorldId());
        assertEquals(1000, config.statusWriteIntervalMillis());
        assertEquals(5000, config.statusStaleAfterMillis());
        assertEquals(root.resolve("NpcRuntimeHarness").resolve("requests"), config.paths().requests());
        assertEquals(root.resolve("NpcRuntimeHarness").resolve("results"), config.paths().results());
        assertEquals(root.resolve("NpcRuntimeHarness").resolve("status"), config.paths().status());
        assertEquals(root.resolve("NpcRuntimeHarness").resolve("status").resolve("harness-status.json"), config.paths().statusFile());
    }

    @Test
    void validatesTickAndEntityBounds() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        assertEquals(200, config.clampTicks(200));
        assertThrows(IllegalArgumentException.class, () -> config.clampTicks(0));
        assertThrows(IllegalArgumentException.class, () -> config.clampTicks(1201));
        assertThrows(IllegalArgumentException.class, () -> config.validateEntityCount(65));
    }

    @Test
    void autoEnableCanBeReadFromDevSystemProperty() {
        String previous = System.getProperty("alec.npcRuntime.autoEnable");
        try {
            System.setProperty("alec.npcRuntime.autoEnable", "true");

            NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

            assertTrue(config.autoEnable());
            assertTrue(config.initiallyEnabled());
        } finally {
            if (previous == null) {
                System.clearProperty("alec.npcRuntime.autoEnable");
            } else {
                System.setProperty("alec.npcRuntime.autoEnable", previous);
            }
        }
    }
}
