package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshotService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Live runtime runner: prepares the test world, advances one bounded observation step at a time, and cleans up.
 */
public final class NpcRuntimeLiveScenarioRunner implements NpcRuntimeHarnessService.ScenarioRunner {
    private static final long WORLD_THREAD_TIMEOUT_SECONDS = 30L;

    private final NpcRuntimeHarnessConfig config;
    private final NpcRuntimeFlatworldManager flatworldManager;
    private final NpcRuntimeFixtureSpawner fixtureSpawner;
    private final NpcDebugSnapshotService snapshotService;
    private final NpcRuntimeTickScheduler tickScheduler;

    public NpcRuntimeLiveScenarioRunner(@Nonnull NpcRuntimeHarnessConfig config,
                                        @Nonnull NpcDebugSnapshotService snapshotService) {
        this(config, new NpcRuntimeFlatworldManager(), new NpcRuntimeFixtureSpawner(), snapshotService, new NpcRuntimeTickScheduler());
    }

    NpcRuntimeLiveScenarioRunner(@Nonnull NpcRuntimeHarnessConfig config,
                                 @Nonnull NpcRuntimeFlatworldManager flatworldManager,
                                 @Nonnull NpcRuntimeFixtureSpawner fixtureSpawner,
                                 @Nonnull NpcDebugSnapshotService snapshotService,
                                 @Nonnull NpcRuntimeTickScheduler tickScheduler) {
        this.config = config;
        this.flatworldManager = flatworldManager;
        this.fixtureSpawner = fixtureSpawner;
        this.snapshotService = snapshotService;
        this.tickScheduler = tickScheduler;
    }

    @Nonnull
    @Override
    public NpcRuntimeResult run(@Nonnull NpcRuntimeRequest request) throws Exception {
        Path tracePath = config.paths().traces().resolve(request.requestId() + ".trace.jsonl");
        World world = flatworldManager.ensureWorld(request.world().instanceId());
        NpcRuntimeWorldReadiness readiness = NpcRuntimeWorldReadiness.fromWorld(
                request.world().instanceId(),
                world.getName(),
                world.isTicking(),
                world.isPaused(),
                world.getPlayerCount()
        );
        if (!readiness.ready()) {
            throw new IllegalStateException("harness-world-not-ready: " + readiness.displayReason());
        }
        ensureArenaChunkLoaded(world, request);
        return runOnPreparedWorld(request, tracePath, world);
    }

    @Nonnull
    private NpcRuntimeResult runOnPreparedWorld(@Nonnull NpcRuntimeRequest request,
                                                @Nonnull Path tracePath,
                                                @Nonnull World world) throws Exception {
        NpcRuntimeScenarioRun run = NpcRuntimeScenarioRun.start(request.requestId(), world.getName(), request.ticks());
        NpcRuntimeObservationCadence cadence = NpcRuntimeObservationCadence.from(request);
        SpawnedNpcHolder spawnedNpc = new SpawnedNpcHolder();

        try (NpcRuntimeTraceWriter writer = NpcRuntimeTraceWriter.open(tracePath, cadence.maxTraceBytes())) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "run-start")
                    .with("assetId", request.assetId())
                    .with("roleId", request.roleId())
                    .with("ticksRequested", request.ticks()));

            NpcRuntimeTickScheduler.RunSummary summary = tickScheduler.run(
                    run,
                    step -> executeWorldStep(world, step),
                    tick -> runOneTick(request, world, writer, run, cadence, spawnedNpc, tick)
            );

            boolean cleanupSucceeded = cleanupOnWorldThread(world, spawnedNpc.npc);
            spawnedNpc.npc = null;
            run.markCleanup(cleanupSucceeded, cleanupSucceeded ? "removed spawned fixtures" : "cleanup failed");
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "cleanup")
                    .with("attempted", run.cleanupAttempted())
                    .with("succeeded", run.cleanupSucceeded())
                    .with("message", run.cleanupMessage()));
            if (!cleanupSucceeded) {
                writer.write(NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                        .with("status", "failed")
                        .with("error", "cleanup failed")
                        .with("ticksRun", summary.ticksRun()));
                throw new IllegalStateException("NPC runtime scenario completed but cleanup failed");
            }

            if (summary.canceled()) {
                writer.write(NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                        .with("status", "canceled")
                        .with("reason", summary.cancelReason())
                        .with("ticksRun", summary.ticksRun()));
                return NpcRuntimeResult.canceled(
                        request.requestId(),
                        request.ticks(),
                        summary.ticksRun(),
                        summary.cancelReason() != null ? summary.cancelReason() : "canceled",
                        NpcRuntimeResult.Cleanup.succeeded(run.cleanupMessage())
                );
            }

            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                    .with("status", "passed")
                    .with("ticksRun", summary.ticksRun())
                    .with("mode", "tick-driven"));
            return NpcRuntimeResult.passed(request, summary.ticksRun(), tracePath, NpcRuntimeResult.Summary.empty());
        } finally {
            cleanupOnWorldThread(world, spawnedNpc.npc);
        }
    }

    @Nonnull
    private NpcRuntimeTickScheduler.TickOutcome runOneTick(@Nonnull NpcRuntimeRequest request,
                                                           @Nonnull World world,
                                                           @Nonnull NpcRuntimeTraceWriter writer,
                                                           @Nonnull NpcRuntimeScenarioRun run,
                                                           @Nonnull NpcRuntimeObservationCadence cadence,
                                                           @Nonnull SpawnedNpcHolder spawnedNpc,
                                                           int tick) throws Exception {
        Store<EntityStore> store = world.getEntityStore().getStore();

        if (tick == 0) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "world-ready")
                    .with("world", world.getName())
                    .with("ticking", world.isTicking())
                    .with("paused", world.isPaused())
                    .with("playerCount", world.getPlayerCount())
                    .with("chunkResidency", "world-config-canUnloadChunks=false; explicit chunk ticket not confirmed"));
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "arena-reset")
                    .with("arena", request.world().arena())
                    .with("mode", "no-block-reset-yet"));
            spawnedNpc.npc = fixtureSpawner.spawnNpcUnderTest(world, request);
            if (spawnedNpc.npc.uuid() != null) {
                run.addNpc("npcUnderTest", spawnedNpc.npc.uuid());
            }
            run.addFixture("npcUnderTest");
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "fixture-spawn")
                    .with("fixture", "npcUnderTest")
                    .with("roleId", request.roleId())
                    .with("npcUuid", spawnedNpc.npc.uuid() != null ? spawnedNpc.npc.uuid().toString() : null));
        }

        if (cadence.includeEvents()) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "tick-start"));
        }

        if (cadence.shouldRecordSnapshot(tick) && spawnedNpc.npc != null) {
            NpcDebugSnapshot snapshot = snapshotService.capture(spawnedNpc.npc.uuid(), spawnedNpc.npc.ref(), store);
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "npc-snapshot")
                    .with("npcUuid", spawnedNpc.npc.uuid() != null ? spawnedNpc.npc.uuid().toString() : null)
                    .with("title", snapshot.title())
                    .with("subtitle", snapshot.subtitle())
                    .with("details", snapshot.details()));
        }

        if (cadence.includeEvents()) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "tick-end"));
        }

        return run.canceled()
                ? NpcRuntimeTickScheduler.TickOutcome.canceled(run.cancelReason() != null ? run.cancelReason() : "canceled")
                : NpcRuntimeTickScheduler.TickOutcome.continueRunning();
    }

    @Nonnull
    private NpcRuntimeTickScheduler.TickOutcome executeWorldStep(@Nonnull World world,
                                                                 @Nonnull NpcRuntimeTickScheduler.StepCallable call) throws Exception {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store.isInThread()) {
            return call.run();
        }

        CompletableFuture<NpcRuntimeTickScheduler.TickOutcome> result = new CompletableFuture<>();
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

    private boolean cleanupOnWorldThread(@Nonnull World world,
                                         @Nullable NpcRuntimeFixtureSpawner.SpawnedNpc spawnedNpc) {
        if (spawnedNpc == null) {
            return true;
        }
        try {
            executeWorldStep(world, () -> {
                fixtureSpawner.cleanup(world.getEntityStore().getStore(), spawnedNpc);
                return NpcRuntimeTickScheduler.TickOutcome.continueRunning();
            });
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void ensureArenaChunkLoaded(@Nonnull World world, @Nonnull NpcRuntimeRequest request) throws Exception {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(
                coordinate(request.fixtures().npc().position(), 0, "fixtures.npc.position"),
                coordinate(request.fixtures().npc().position(), 2, "fixtures.npc.position")
        );
        try {
            world.getChunkAsync(chunkIndex).get(WORLD_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw new IllegalStateException("harness-arena-chunk-not-ready: failed to load chunk " + chunkIndex, checked);
            }
            throw new IllegalStateException("harness-arena-chunk-not-ready: failed to load chunk " + chunkIndex, cause);
        }
        if (world.getChunkIfLoaded(chunkIndex) == null && world.getChunkIfNonTicking(chunkIndex) == null && world.getChunkIfInMemory(chunkIndex) == null) {
            throw new IllegalStateException("harness-arena-chunk-not-ready: chunk " + chunkIndex + " is not resident after load");
        }
    }

    private double coordinate(@Nonnull java.util.List<Object> values, int index, @Nonnull String fieldName) {
        if (values.size() != 3) {
            throw new IllegalArgumentException(fieldName + " must contain exactly 3 numbers");
        }
        Object value = values.get(index);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(fieldName + " must contain only numbers");
    }

    private static final class SpawnedNpcHolder {
        @Nullable
        private NpcRuntimeFixtureSpawner.SpawnedNpc npc;
    }
}
