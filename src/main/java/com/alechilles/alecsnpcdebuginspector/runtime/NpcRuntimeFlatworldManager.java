package com.alechilles.alecsnpcdebuginspector.runtime;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Locates or creates the dedicated flatworld used by live NPC runtime scenarios.
 */
public final class NpcRuntimeFlatworldManager {
    private static final long WORLD_LOAD_TIMEOUT_SECONDS = 30L;

    @Nonnull
    public World ensureWorld(@Nonnull String instanceId) throws Exception {
        Universe universe = Universe.get();
        if (universe == null) {
            throw new IllegalStateException("Universe is not available yet");
        }

        World existing = universe.getWorld(instanceId);
        if (existing != null) {
            return prepareWorld(existing);
        }

        World loaded = universe.isWorldLoadable(instanceId)
                ? universe.loadWorld(instanceId).get(WORLD_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                : createWorld(universe, instanceId);
        return prepareWorld(loaded);
    }

    @Nonnull
    private World createWorld(@Nonnull Universe universe, @Nonnull String instanceId) throws Exception {
        WorldConfig config = createFlatworldConfig(instanceId);
        Path worldPath = universe.validateWorldPath(instanceId);
        return universe.makeWorld(instanceId, worldPath, config).get(WORLD_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Nonnull
    private static WorldConfig createFlatworldConfig(@Nonnull String instanceId) throws Exception {
        FlatWorldGenProvider flatWorldGenProvider = new FlatWorldGenProvider();
        WorldConfig config = new WorldConfig();
        config.setDisplayName("NPC Runtime Test Flatworld (" + instanceId + ")");
        config.setWorldGenProvider(flatWorldGenProvider);
        config.setDefaultSpawnProvider(flatWorldGenProvider.getGenerator());
        config.setTicking(true);
        config.setGameTimePaused(true);
        config.setForcedWeather("clear");
        config.setCanUnloadChunks(false);
        config.setCanSaveChunks(false);
        config.setSaveNewChunks(false);
        config.setSpawningNPC(false);
        config.setDeleteOnRemove(true);
        return config;
    }

    @Nonnull
    private static World prepareWorld(@Nonnull World world) {
        world.setTicking(true);
        world.setPaused(false);
        WorldConfig config = world.getWorldConfig();
        config.setTicking(true);
        config.setGameTimePaused(true);
        config.setForcedWeather("clear");
        config.setCanUnloadChunks(false);
        config.setCanSaveChunks(false);
        config.setSaveNewChunks(false);
        config.markChanged();
        return world;
    }
}
