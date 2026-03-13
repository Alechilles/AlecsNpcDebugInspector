package com.alechilles.alecsnpcdebuginspector.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HStatsServerUuidFileTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsUuidWhenEnabled() throws Exception {
        Path file = tempDir.resolve("hstats-server-uuid.txt");
        Files.writeString(
                file,
                "HStats - Hytale Mod Metrics (hstats.dev)\n"
                        + "Description line\n"
                        + "\n"
                        + "enabled=true\n"
                        + "11111111-2222-3333-4444-555555555555\n"
        );

        String uuid = HStatsServerUuidFile.readEnabledServerUuid(file);
        assertEquals("11111111-2222-3333-4444-555555555555", uuid);
    }

    @Test
    void returnsNullWhenDisabled() throws Exception {
        Path file = tempDir.resolve("hstats-server-uuid.txt");
        Files.writeString(
                file,
                "HStats - Hytale Mod Metrics (hstats.dev)\n"
                        + "Description line\n"
                        + "\n"
                        + "enabled=false\n"
                        + "11111111-2222-3333-4444-555555555555\n"
        );

        assertNull(HStatsServerUuidFile.readEnabledServerUuid(file));
    }

    @Test
    void returnsNullWhenFileDoesNotExist() {
        Path file = tempDir.resolve("missing.txt");
        assertNull(HStatsServerUuidFile.readEnabledServerUuid(file));
    }
}
