package com.alechilles.alecsnpcdebuginspector;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshotService;
import com.alechilles.alecsnpcdebuginspector.commands.NpcDebugCommand;
import com.alechilles.alecsnpcdebuginspector.interactions.NpcDebugInspectorItemInteraction;
import com.alechilles.alecsnpcdebuginspector.items.NpcDebugItemFeatureHandler;
import com.alechilles.alecsnpcdebuginspector.metrics.NpcDebugInspectorHStatsIntegration;
import com.alechilles.alecsnpcdebuginspector.runtime.NpcRuntimeCommand;
import com.alechilles.alecsnpcdebuginspector.runtime.NpcRuntimeHarnessConfig;
import com.alechilles.alecsnpcdebuginspector.runtime.NpcRuntimeHarnessService;
import com.alechilles.alecsnpcdebuginspector.runtime.NpcRuntimeLiveScenarioRunner;
import com.alechilles.alecsnpcdebuginspector.ui.NpcDebugHighlightManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.nio.file.Path;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Main entry point for Alec's NPC Debug Inspector.
 */
public final class AlecsNpcDebugInspector extends JavaPlugin {
    private static AlecsNpcDebugInspector instance;
    private NpcDebugSnapshotService snapshotService;
    private NpcDebugItemFeatureHandler itemFeatureHandler;
    private NpcDebugInspectorHStatsIntegration hStatsIntegration;
    private NpcRuntimeHarnessService runtimeHarnessService;

    public AlecsNpcDebugInspector(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        snapshotService = new NpcDebugSnapshotService();
        Interaction.CODEC.register(
                "NpcDebugInspectorItem",
                NpcDebugInspectorItemInteraction.class,
                NpcDebugInspectorItemInteraction.CODEC
        );
        itemFeatureHandler = new NpcDebugItemFeatureHandler(snapshotService);
        hStatsIntegration = new NpcDebugInspectorHStatsIntegration(this);
        NpcRuntimeHarnessConfig runtimeConfig = NpcRuntimeHarnessConfig.developmentDefault(defaultUserDataPath());
        runtimeHarnessService = new NpcRuntimeHarnessService(
                runtimeConfig,
                new NpcRuntimeLiveScenarioRunner(runtimeConfig, snapshotService)
        );
        if (getCommandRegistry() != null) {
            getCommandRegistry().registerCommand(new NpcDebugCommand(snapshotService));
            getCommandRegistry().registerCommand(new NpcRuntimeCommand(runtimeHarnessService));
        }
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("Alec's NPC Debug Inspector enabled.");
        if (hStatsIntegration != null) {
            hStatsIntegration.initialize();
        }
        if (runtimeHarnessService != null) {
            try {
                runtimeHarnessService.initializeDirectories();
            } catch (Exception exception) {
                getLogger().at(Level.WARNING).log("Could not initialize NPC runtime harness directories.", exception);
            }
        }
    }

    @Override
    protected void shutdown() {
        NpcDebugHighlightManager.stopAll();
        if (snapshotService != null) {
            snapshotService.close();
        }
        getLogger().at(Level.INFO).log("Alec's NPC Debug Inspector disabled.");
    }

    public static AlecsNpcDebugInspector getInstance() {
        return instance;
    }

    public NpcDebugItemFeatureHandler getItemFeatureHandler() {
        return itemFeatureHandler;
    }

    public NpcDebugSnapshotService getSnapshotService() {
        return snapshotService;
    }

    public NpcRuntimeHarnessService getRuntimeHarnessService() {
        return runtimeHarnessService;
    }

    private static Path defaultUserDataPath() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, "AppData", "Roaming", "Hytale", "UserData");
    }
}
