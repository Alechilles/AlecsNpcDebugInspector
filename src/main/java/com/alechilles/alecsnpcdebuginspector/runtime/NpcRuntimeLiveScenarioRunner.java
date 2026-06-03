package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshotService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * First live-runtime runner: prepares the test world, spawns one NPC, records one snapshot, and cleans up.
 */
public final class NpcRuntimeLiveScenarioRunner implements NpcRuntimeHarnessService.ScenarioRunner {
    private static final long WORLD_THREAD_TIMEOUT_SECONDS = 30L;

    private final NpcRuntimeHarnessConfig config;
    private final NpcRuntimeFlatworldManager flatworldManager;
    private final NpcRuntimeFixtureSpawner fixtureSpawner;
    private final NpcDebugSnapshotService snapshotService;

    public NpcRuntimeLiveScenarioRunner(@Nonnull NpcRuntimeHarnessConfig config,
                                        @Nonnull NpcDebugSnapshotService snapshotService) {
        this(config, new NpcRuntimeFlatworldManager(), new NpcRuntimeFixtureSpawner(), snapshotService);
    }

    NpcRuntimeLiveScenarioRunner(@Nonnull NpcRuntimeHarnessConfig config,
                                 @Nonnull NpcRuntimeFlatworldManager flatworldManager,
                                 @Nonnull NpcRuntimeFixtureSpawner fixtureSpawner,
                                 @Nonnull NpcDebugSnapshotService snapshotService) {
        this.config = config;
        this.flatworldManager = flatworldManager;
        this.fixtureSpawner = fixtureSpawner;
        this.snapshotService = snapshotService;
    }

    @Nonnull
    @Override
    public NpcRuntimeResult run(@Nonnull NpcRuntimeRequest request) throws Exception {
        Path tracePath = config.paths().traces().resolve(request.requestId() + ".trace.jsonl");
        World world = flatworldManager.ensureWorld(request.world().instanceId());
        return runOnWorldThread(world, () -> runOnPreparedWorld(request, tracePath, world));
    }

    @Nonnull
    private NpcRuntimeResult runOnPreparedWorld(@Nonnull NpcRuntimeRequest request,
                                                @Nonnull Path tracePath,
                                                @Nonnull World world) throws Exception {
        NpcRuntimeFixtureSpawner.SpawnedNpc spawnedNpc = null;
        Store<EntityStore> store = world.getEntityStore().getStore();

        try (NpcRuntimeTraceWriter writer = NpcRuntimeTraceWriter.open(tracePath)) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "run-start")
                    .with("assetId", request.assetId())
                    .with("roleId", request.roleId()));

            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "world-ready")
                    .with("world", world.getName())
                    .with("ticking", world.isTicking())
                    .with("paused", world.isPaused())
                    .with("playerCount", world.getPlayerCount())
                    .with("chunkResidency", "world-config-canUnloadChunks=false; explicit chunk ticket not confirmed"));

            spawnedNpc = fixtureSpawner.spawnNpcUnderTest(world, request);
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "fixture-spawn")
                    .with("fixture", "npc")
                    .with("roleId", request.roleId())
                    .with("npcUuid", spawnedNpc.uuid() != null ? spawnedNpc.uuid().toString() : null));

            NpcDebugSnapshot snapshot = snapshotService.capture(spawnedNpc.uuid(), spawnedNpc.ref(), store);
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "npc-snapshot")
                    .with("npcUuid", spawnedNpc.uuid() != null ? spawnedNpc.uuid().toString() : null)
                    .with("title", snapshot.title())
                    .with("subtitle", snapshot.subtitle())
                    .with("details", snapshot.details()));

            boolean cleanupSucceeded = cleanup(store, spawnedNpc);
            spawnedNpc = null;
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "cleanup")
                    .with("succeeded", cleanupSucceeded));
            if (!cleanupSucceeded) {
                writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "run-end")
                        .with("status", "failed")
                        .with("error", "cleanup failed")
                        .with("ticksRun", 0));
                throw new IllegalStateException("NPC runtime scenario completed but cleanup failed");
            }

            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "run-end")
                    .with("status", "passed")
                    .with("ticksRun", 0)
                    .with("mode", "one-shot-spawn-snapshot"));
            return NpcRuntimeResult.passed(request, 0, tracePath, NpcRuntimeResult.Summary.empty());
        } finally {
            cleanup(store, spawnedNpc);
        }
    }

    @Nonnull
    private NpcRuntimeResult runOnWorldThread(@Nonnull World world, @Nonnull ScenarioCall call) throws Exception {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store.isInThread()) {
            return call.run();
        }

        CompletableFuture<NpcRuntimeResult> result = new CompletableFuture<>();
        world.execute(() -> {
            try {
                result.complete(call.run());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        try {
            return result.get(WORLD_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    private boolean cleanup(@Nullable Store<EntityStore> store,
                            @Nullable NpcRuntimeFixtureSpawner.SpawnedNpc spawnedNpc) {
        if (store == null || spawnedNpc == null) {
            return true;
        }
        try {
            fixtureSpawner.cleanup(store, spawnedNpc);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @FunctionalInterface
    private interface ScenarioCall {
        @Nonnull
        NpcRuntimeResult run() throws Exception;
    }
}
