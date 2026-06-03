package com.alechilles.alecsnpcdebuginspector.runtime;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NpcRuntimeHarnessConfigTest {
    @Test
    void defaultsAreSafeAndResolveHarnessDirectories() {
        Path root = Path.of("build", "test-userdata");

        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(root);

        assertFalse(config.enabledByDefault());
        assertEquals(1200, config.maxTicks());
        assertEquals(64, config.maxEntities());
        assertEquals("npc_runtime_test_flatworld", config.instanceId());
        assertEquals(root.resolve("NpcRuntimeHarness").resolve("requests"), config.paths().requests());
        assertEquals(root.resolve("NpcRuntimeHarness").resolve("results"), config.paths().results());
    }

    @Test
    void validatesTickAndEntityBounds() {
        NpcRuntimeHarnessConfig config = NpcRuntimeHarnessConfig.developmentDefault(Path.of("build", "test-userdata"));

        assertEquals(200, config.clampTicks(200));
        assertThrows(IllegalArgumentException.class, () -> config.clampTicks(0));
        assertThrows(IllegalArgumentException.class, () -> config.clampTicks(1201));
        assertThrows(IllegalArgumentException.class, () -> config.validateEntityCount(65));
    }
}
