package com.alechilles.alecsnpcdebuginspector;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshotService;
import com.alechilles.alecsnpcdebuginspector.commands.NpcDebugCommand;
import com.alechilles.alecsnpcdebuginspector.interactions.NpcDebugInspectorItemInteraction;
import com.alechilles.alecsnpcdebuginspector.items.NpcDebugItemFeatureHandler;
import com.alechilles.alecsnpcdebuginspector.metrics.NpcDebugInspectorHStatsIntegration;
import com.alechilles.alecsnpcdebuginspector.ui.NpcDebugHighlightManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
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
        if (getCommandRegistry() != null) {
            getCommandRegistry().registerCommand(new NpcDebugCommand(snapshotService));
        }
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("Alec's NPC Debug Inspector enabled.");
        if (hStatsIntegration != null) {
            hStatsIntegration.initialize();
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
}
