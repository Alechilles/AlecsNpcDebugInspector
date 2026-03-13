package com.alechilles.alecsnpcdebuginspector.metrics;

import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Bootstraps HStats metrics reporting for Alec's NPC Inspector.
 */
public final class NpcDebugInspectorHStatsIntegration {

    private static final String DEFAULT_HSTATS_PLUGIN_UUID = "ebe7c1a2-4009-4e22-8991-05f6566e230d";
    private static final String HSTATS_UUID_SYSTEM_PROPERTY = "alecsnpcdebuginspector.hstats.uuid";
    private static final String HSTATS_UUID_ENV_VAR = "ALECS_NPC_INSPECTOR_HSTATS_UUID";
    private static final Path HSTATS_SERVER_UUID_FILE = Path.of("hstats-server-uuid.txt");

    private final JavaPlugin plugin;
    private boolean initialized;

    public NpcDebugInspectorHStatsIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (initialized || plugin == null) {
            return;
        }
        String pluginUuid = resolveHStatsPluginUuid();
        if (pluginUuid == null) {
            initialized = true;
            plugin.getLogger().at(Level.INFO).log(
                    "NPC Inspector metrics skipped: configure HStats UUID via -D"
                            + HSTATS_UUID_SYSTEM_PROPERTY
                            + " or "
                            + HSTATS_UUID_ENV_VAR
                            + "."
            );
            return;
        }
        String version = resolvePluginVersion();
        try {
            new HStats(pluginUuid, version);
            initialized = true;
            if (HStatsServerUuidFile.readEnabledServerUuid(HSTATS_SERVER_UUID_FILE) == null) {
                plugin.getLogger().at(Level.INFO).log(
                        "NPC Inspector metrics are disabled by server config (hstats-server-uuid.txt)."
                );
                return;
            }
            plugin.getLogger().at(Level.INFO).log(
                    "NPC Inspector metrics enabled via HStats. Server owners can opt out in hstats-server-uuid.txt."
            );
        } catch (Exception ex) {
            plugin.getLogger().at(Level.WARNING).withCause(ex)
                    .log("NPC Inspector metrics failed to initialize; continuing without HStats.");
        }
    }

    private String resolvePluginVersion() {
        PluginManifest manifest = plugin.getManifest();
        if (manifest == null) {
            return "Unknown";
        }
        Semver version = manifest.getVersion();
        if (version == null) {
            return "Unknown";
        }
        return version.toString();
    }

    private String resolveHStatsPluginUuid() {
        String systemPropertyUuid = sanitizeUuid(System.getProperty(HSTATS_UUID_SYSTEM_PROPERTY));
        if (systemPropertyUuid != null) {
            return systemPropertyUuid;
        }
        String envUuid = sanitizeUuid(System.getenv(HSTATS_UUID_ENV_VAR));
        if (envUuid != null) {
            return envUuid;
        }
        return sanitizeUuid(DEFAULT_HSTATS_PLUGIN_UUID);
    }

    private String sanitizeUuid(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            return null;
        }
        return value;
    }
}
