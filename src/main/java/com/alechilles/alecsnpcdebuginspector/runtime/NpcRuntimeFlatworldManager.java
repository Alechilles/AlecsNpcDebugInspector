package com.alechilles.alecsnpcdebuginspector.runtime;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Locates or creates the dedicated flatworld used by live NPC runtime scenarios.
 */
public final class NpcRuntimeFlatworldManager {
    private static final long WORLD_LOAD_TIMEOUT_SECONDS = 30L;

    @Nonnull
    public NpcRuntimeWorldReadiness ensureWorldReady(@Nonnull String instanceId) {
        Universe universe = Universe.get();
        if (universe == null) {
            return NpcRuntimeWorldReadiness.notReady(
                    instanceId,
                    NpcRuntimeWorldReadiness.UNIVERSE_NOT_AVAILABLE,
                    "Universe is not available yet"
            );
        }
        NpcRuntimeWorldReadiness universeReadiness = universeReadiness(universe, instanceId);
        if (!universeReadiness.ready()) {
            return universeReadiness;
        }
        try {
            World world = ensureWorld(instanceId);
            return readinessFromWorld(instanceId, world);
        } catch (Exception exception) {
            return NpcRuntimeWorldReadiness.notReady(
                    instanceId,
                    NpcRuntimeWorldReadiness.WORLD_LOAD_FAILED,
                    exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName()
            );
        }
    }

    @Nonnull
    public NpcRuntimeWorldReadiness currentReadiness(@Nonnull String instanceId) {
        Universe universe = Universe.get();
        if (universe == null) {
            return NpcRuntimeWorldReadiness.notReady(
                    instanceId,
                    NpcRuntimeWorldReadiness.UNIVERSE_NOT_AVAILABLE,
                    "Universe is not available yet"
            );
        }
        NpcRuntimeWorldReadiness universeReadiness = universeReadiness(universe, instanceId);
        if (!universeReadiness.ready()) {
            return universeReadiness;
        }
        World world = universe.getWorld(instanceId);
        if (world == null) {
            return NpcRuntimeWorldReadiness.notReady(
                    instanceId,
                    NpcRuntimeWorldReadiness.WORLD_NOT_LOADED,
                    "World " + instanceId + " is not loaded"
            );
        }
        return readinessFromWorld(instanceId, world);
    }

    @Nonnull
    public World ensureWorld(@Nonnull String instanceId) throws Exception {
        Universe universe = Universe.get();
        if (universe == null) {
            throw new IllegalStateException("Universe is not available yet");
        }
        NpcRuntimeWorldReadiness universeReadiness = universeReadiness(universe, instanceId);
        if (!universeReadiness.ready()) {
            throw new IllegalStateException(universeReadiness.displayReason());
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
    private static NpcRuntimeWorldReadiness universeReadiness(@Nonnull Universe universe, @Nonnull String instanceId) {
        CompletableFuture<Void> ready = universe.getUniverseReady();
        if (ready == null || !ready.isDone()) {
            return NpcRuntimeWorldReadiness.notReady(
                    instanceId,
                    NpcRuntimeWorldReadiness.UNIVERSE_NOT_READY,
                    "Universe startup future is not complete"
            );
        }
        try {
            ready.get(0, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            return NpcRuntimeWorldReadiness.notReady(
                    instanceId,
                    NpcRuntimeWorldReadiness.UNIVERSE_NOT_READY,
                    cause != null && cause.getMessage() != null ? cause.getMessage() : exception.getMessage()
            );
        } catch (Exception exception) {
            return NpcRuntimeWorldReadiness.notReady(
                    instanceId,
                    NpcRuntimeWorldReadiness.UNIVERSE_NOT_READY,
                    exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName()
            );
        }
        return NpcRuntimeWorldReadiness.ready(instanceId, 0);
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
        config.setGameTimePaused(false);
        config.setForcedWeather("clear");
        config.setCanUnloadChunks(false);
        config.setCanSaveChunks(false);
        config.setSaveNewChunks(false);
        config.setSpawningNPC(false);
        config.setIsAllNPCFrozen(false);
        config.setDeleteOnRemove(true);
        return config;
    }

    @Nonnull
    private static World prepareWorld(@Nonnull World world) {
        world.setTicking(true);
        world.setPaused(false);
        WorldConfig config = world.getWorldConfig();
        config.setTicking(true);
        config.setGameTimePaused(false);
        config.setForcedWeather("clear");
        config.setCanUnloadChunks(false);
        config.setCanSaveChunks(false);
        config.setSaveNewChunks(false);
        config.setSpawningNPC(false);
        config.setIsAllNPCFrozen(false);
        config.markChanged();
        return world;
    }

    @Nonnull
    private static NpcRuntimeWorldReadiness readinessFromWorld(@Nonnull String expectedWorldId, @Nonnull World world) {
        return NpcRuntimeWorldReadiness.fromWorld(
                expectedWorldId,
                world.getName(),
                world.isTicking(),
                world.isPaused(),
                world.getPlayerCount()
        );
    }
}
