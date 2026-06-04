package com.alechilles.alecsnpcdebuginspector.runtime;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/**
 * Locates or creates the dedicated flatworld used by live NPC runtime scenarios.
 */
public final class NpcRuntimeFlatworldManager {
    private static final long WORLD_LOAD_TIMEOUT_SECONDS = 30L;
    private static final Pattern SCIENTIFIC_SEED = Pattern.compile("\"Seed\"\\s*:\\s*([0-9]+)\\.([0-9]+)E([0-9]+)");

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

        Path worldPath = universe.validateWorldPath(instanceId);
        World loaded;
        if (universe.isWorldLoadable(instanceId)) {
            repairPersistedWorldConfig(worldPath);
            loaded = universe.loadWorld(instanceId).get(WORLD_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } else {
            loaded = createWorld(universe, instanceId, worldPath);
        }
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
    private World createWorld(@Nonnull Universe universe, @Nonnull String instanceId, @Nonnull Path worldPath) throws Exception {
        WorldConfig config = createFlatworldConfig(instanceId);
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
        config.setCanUnloadChunks(false);
        config.setCanSaveChunks(false);
        config.setSaveNewChunks(false);
        config.setSpawningNPC(false);
        config.setIsAllNPCFrozen(false);
        config.markChanged();
        return world;
    }

    static boolean repairPersistedWorldConfig(@Nonnull Path worldPath) throws Exception {
        Path configPath = worldPath.resolve("config.json");
        if (!Files.isRegularFile(configPath)) {
            return false;
        }
        String original = Files.readString(configPath, StandardCharsets.UTF_8);
        String repaired = original
                .replaceAll("(?s),\\s*\"ForcedWeather\"\\s*:\\s*\"clear\"", "")
                .replaceAll("(?s)\"ForcedWeather\"\\s*:\\s*\"clear\"\\s*,", "")
                .replaceAll("\"Version\"\\s*:\\s*(\\d+)\\.0\\b", "\"Version\":$1")
                .replaceAll("\"(From|To)\"\\s*:\\s*(\\d+)\\.0\\b", "\"$1\":$2");
        repaired = repairScientificSeed(repaired);
        if (original.equals(repaired)) {
            return false;
        }
        Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".npc-runtime-repair.tmp");
        Files.writeString(tempPath, repaired, StandardCharsets.UTF_8);
        Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    @Nonnull
    private static String repairScientificSeed(@Nonnull String config) {
        Matcher matcher = SCIENTIFIC_SEED.matcher(config);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String digits = matcher.group(1) + matcher.group(2);
            int exponent = Integer.parseInt(matcher.group(3));
            int decimalDigits = matcher.group(2).length();
            int zeroesToAppend = Math.max(0, exponent - decimalDigits);
            String replacement = "\"Seed\":" + digits + "0".repeat(zeroesToAppend);
            matcher.appendReplacement(out, replacement);
        }
        matcher.appendTail(out);
        return out.toString();
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
