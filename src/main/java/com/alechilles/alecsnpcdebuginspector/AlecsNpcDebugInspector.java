package com.alechilles.alecsnpcdebuginspector;

import com.alechilles.alecsnpcdebuginspector.commands.NpcDebugCommand;
import com.alechilles.alecsnpcdebuginspector.interactions.NpcDebugInspectorItemInteraction;
import com.alechilles.alecsnpcdebuginspector.items.NpcDebugItemFeatureHandler;
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
    private NpcDebugItemFeatureHandler itemFeatureHandler;

    public AlecsNpcDebugInspector(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        Interaction.CODEC.register(
                "NpcDebugInspectorItem",
                NpcDebugInspectorItemInteraction.class,
                NpcDebugInspectorItemInteraction.CODEC
        );
        itemFeatureHandler = new NpcDebugItemFeatureHandler();
        if (getCommandRegistry() != null) {
            getCommandRegistry().registerCommand(new NpcDebugCommand());
        }
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("Alec's NPC Debug Inspector enabled.");
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("Alec's NPC Debug Inspector disabled.");
    }

    public static AlecsNpcDebugInspector getInstance() {
        return instance;
    }

    public NpcDebugItemFeatureHandler getItemFeatureHandler() {
        return itemFeatureHandler;
    }
}
