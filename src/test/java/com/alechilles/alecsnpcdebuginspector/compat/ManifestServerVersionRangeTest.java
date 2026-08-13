package com.alechilles.alecsnpcdebuginspector.compat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.semver.SemverRange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

class ManifestServerVersionRangeTest {
    @Test
    void manifestRangeMatchesSupportedServerVersions() throws IOException {
        BsonDocument document = BsonDocument.parse(
                Files.readString(Path.of("src", "main", "resources", "manifest.json"))
        );
        PluginManifest manifest = PluginManifest.CODEC.decode(document);
        SemverRange range = manifest.getServerVersion();

        assertAll(
                () -> assertTrue(satisfies(range, "0.5.7")),
                () -> assertTrue(satisfies(range, "0.6.0-pre.11")),
                () -> assertTrue(satisfies(range, "0.6.0")),
                () -> assertFalse(satisfies(range, "0.5.6")),
                () -> assertFalse(satisfies(range, "0.6.0-pre.0")),
                () -> assertFalse(satisfies(range, "0.7.0"))
        );
    }

    private boolean satisfies(SemverRange range, String version) {
        return Semver.fromString(version).satisfies(range);
    }
}
