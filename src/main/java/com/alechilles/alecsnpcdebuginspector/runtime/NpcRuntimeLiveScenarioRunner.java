package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshotService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/**
 * First live-runtime runner: prepares the test world, spawns one NPC, records one snapshot, and cleans up.
 */
public final class NpcRuntimeLiveScenarioRunner implements NpcRuntimeHarnessService.ScenarioRunner {
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
        NpcRuntimeFixtureSpawner.SpawnedNpc spawnedNpc = null;
        Store<EntityStore> store = null;

        try (NpcRuntimeTraceWriter writer = NpcRuntimeTraceWriter.open(tracePath)) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "run-start")
                    .with("assetId", request.assetId())
                    .with("roleId", request.roleId()));

            World world = flatworldManager.ensureWorld(request.world().instanceId());
            store = world.getEntityStore().getStore();
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

    private boolean cleanup(@javax.annotation.Nullable Store<EntityStore> store,
                            @javax.annotation.Nullable NpcRuntimeFixtureSpawner.SpawnedNpc spawnedNpc) {
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
}
