package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshotService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private final NpcRuntimeObserver observer;

    public NpcRuntimeLiveScenarioRunner(@Nonnull NpcRuntimeHarnessConfig config,
                                        @Nonnull NpcDebugSnapshotService snapshotService) {
        this(config, new NpcRuntimeFlatworldManager(), new NpcRuntimeFixtureSpawner(), snapshotService, new NpcRuntimeTickScheduler(), new NpcRuntimeObserver());
    }

    NpcRuntimeLiveScenarioRunner(@Nonnull NpcRuntimeHarnessConfig config,
                                 @Nonnull NpcRuntimeFlatworldManager flatworldManager,
                                 @Nonnull NpcRuntimeFixtureSpawner fixtureSpawner,
                                 @Nonnull NpcDebugSnapshotService snapshotService,
                                 @Nonnull NpcRuntimeTickScheduler tickScheduler,
                                 @Nonnull NpcRuntimeObserver observer) {
        this.config = config;
        this.flatworldManager = flatworldManager;
        this.fixtureSpawner = fixtureSpawner;
        this.snapshotService = snapshotService;
        this.tickScheduler = tickScheduler;
        this.observer = observer;
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
        NpcRuntimeArena arena = ensureArenaChunkLoaded(world, request);
        return runOnPreparedWorld(request, tracePath, world, arena);
    }

    @Nonnull
    private NpcRuntimeResult runOnPreparedWorld(@Nonnull NpcRuntimeRequest request,
                                                @Nonnull Path tracePath,
                                                @Nonnull World world,
                                                @Nonnull NpcRuntimeArena arena) throws Exception {
        NpcRuntimeScenarioRun run = NpcRuntimeScenarioRun.start(request.requestId(), world.getName(), request.ticks());
        NpcRuntimeObservationCadence cadence = NpcRuntimeObservationCadence.from(request);
        NpcRuntimeFixtureRegistry fixtureRegistry = new NpcRuntimeFixtureRegistry();
        SpawnedFixtureHolder spawnedFixtures = new SpawnedFixtureHolder();

        try (NpcRuntimeTraceWriter writer = NpcRuntimeTraceWriter.open(tracePath, cadence.maxTraceBytes())) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), 0, "run-start")
                    .with("assetId", request.assetId())
                    .with("roleId", request.roleId())
                    .with("ticksRequested", request.ticks()));

            NpcRuntimeTickScheduler.RunSummary summary = tickScheduler.run(
                    run,
                    step -> executeWorldStep(world, step),
                    tick -> runOneTick(request, world, writer, run, cadence, fixtureRegistry, spawnedFixtures, arena, tick)
            );

            NpcRuntimeCleanupReport cleanupReport = cleanupOnWorldThread(world, fixtureRegistry, spawnedFixtures.spawnedNpcs);
            spawnedFixtures.spawnedNpcs.clear();
            run.markCleanup(cleanupReport.succeeded(), cleanupReport.message());
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "cleanup")
                    .with("attempted", run.cleanupAttempted())
                    .with("succeeded", run.cleanupSucceeded())
                    .with("message", run.cleanupMessage())
                    .with("report", cleanupReport.toMap()));
            if (!cleanupReport.succeeded()) {
                writer.write(NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                        .with("status", "failed")
                        .with("classification", "cleanup-failed")
                        .with("error", cleanupReport.message())
                        .with("ticksRun", summary.ticksRun()));
                return NpcRuntimeResult.cleanupFailed(request, summary.ticksRun(), tracePath, NpcRuntimeResult.Summary.empty(), cleanupReport);
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
                        NpcRuntimeResult.Cleanup.fromReport(cleanupReport)
                );
            }

            List<NpcRuntimeAssertionResult> assertionResults = NpcRuntimeAssertion.fromSpecs(request.assertions()).stream()
                    .map(assertion -> assertion.evaluate(spawnedFixtures.evidenceRecords))
                    .toList();
            NpcRuntimeResult.Summary resultSummary = NpcRuntimeResult.Summary.empty().withAssertions(assertionResults);
            if (!assertionResults.isEmpty()) {
                writer.write(NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "assertions")
                        .with("results", assertionResults.stream().map(NpcRuntimeAssertionResult::toMap).toList()));
            }
            if (resultSummary.hasAssertionFailures() || resultSummary.hasAssertionUnknowns()) {
                String classification = resultSummary.hasAssertionFailures() ? "assertion-failed" : "assertion-unknown";
                writer.write(NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                        .with("status", "failed")
                        .with("classification", classification)
                        .with("ticksRun", summary.ticksRun()));
                return NpcRuntimeResult.assertionFailed(
                        request,
                        summary.ticksRun(),
                        tracePath,
                        resultSummary,
                        NpcRuntimeResult.Cleanup.fromReport(cleanupReport),
                        classification,
                        "runtime assertions did not all pass"
                );
            }

            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                    .with("status", "passed")
                    .with("ticksRun", summary.ticksRun())
                    .with("mode", "tick-driven"));
            return NpcRuntimeResult.passed(
                    request,
                    summary.ticksRun(),
                    tracePath,
                    resultSummary,
                    NpcRuntimeResult.Cleanup.fromReport(cleanupReport)
            );
        } finally {
            cleanupOnWorldThread(world, fixtureRegistry, spawnedFixtures.spawnedNpcs);
        }
    }

    @Nonnull
    private NpcRuntimeTickScheduler.TickOutcome runOneTick(@Nonnull NpcRuntimeRequest request,
                                                           @Nonnull World world,
                                                           @Nonnull NpcRuntimeTraceWriter writer,
                                                           @Nonnull NpcRuntimeScenarioRun run,
                                                           @Nonnull NpcRuntimeObservationCadence cadence,
                                                           @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry,
                                                           @Nonnull SpawnedFixtureHolder spawnedFixtures,
                                                           @Nonnull NpcRuntimeArena arena,
                                                           int tick) throws Exception {
        Store<EntityStore> store = world.getEntityStore().getStore();

        if (tick == 0) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "world-ready")
                    .with("world", world.getName())
                    .with("ticking", world.isTicking())
                    .with("paused", world.isPaused())
                    .with("playerCount", world.getPlayerCount())
                    .with("chunkResidency", arena.residencyMode())
                    .with("spawnChunkIndex", arena.spawnChunkIndex()));
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "arena-reset")
                    .with("arena", request.world().arena())
                    .with("mode", "no-block-reset-yet")
                    .with("details", arena.toMap()));
            for (NpcRuntimeFixtureSpec fixture : request.fixtures().list()) {
                NpcRuntimeFixtureSpawner.SpawnedNpc spawned = fixtureSpawner.spawnFixture(world, fixture);
                spawnedFixtures.spawnedNpcs.add(spawned);
                if (spawned.uuid() != null) {
                    run.addNpc(fixture.fixtureId(), spawned.uuid());
                }
                fixtureRegistry.recordEntity(fixture.fixtureId(), fixture.kind().jsonName(), spawned.uuid());
                run.addFixture(fixture.fixtureId());
                writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "fixture-spawn")
                        .with("fixture", fixture.fixtureId())
                        .with("fixtureKind", fixture.kind().jsonName())
                        .with("roleId", fixture.roleId())
                        .with("targetSlot", fixture.targetSlot())
                        .with("npcUuid", spawned.uuid() != null ? spawned.uuid().toString() : null)
                        .with("result", spawned.toSpawnResult().toMap()));
            }
        }

        if (cadence.includeEvents()) {
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "tick-start"));
        }

        NpcRuntimeFixtureSpawner.SpawnedNpc npcUnderTest = spawnedFixtures.npcUnderTest();
        if (cadence.shouldRecordSnapshot(tick) && npcUnderTest != null) {
            NpcDebugSnapshot snapshot = snapshotService.capture(npcUnderTest.uuid(), npcUnderTest.ref(), store);
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "npc-snapshot")
                    .with("npcUuid", npcUnderTest.uuid() != null ? npcUnderTest.uuid().toString() : null)
                    .with("title", snapshot.title())
                    .with("subtitle", snapshot.subtitle())
                    .with("details", snapshot.details()));
            NpcRuntimeObservedNpc observed = observer.observe(npcUnderTest.uuid(), snapshot);
            for (NpcRuntimeTraceRecord record : observer.traceRecords(request.requestId(), tick, observed, spawnedFixtures.previousObserved)) {
                writer.write(record);
                if (isAssertionEvidence(record)) {
                    spawnedFixtures.evidenceRecords.add(record.fields());
                }
            }
            spawnedFixtures.previousObserved = observed;
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

    @Nonnull
    private NpcRuntimeCleanupReport cleanupOnWorldThread(@Nonnull World world,
                                                         @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry,
                                                         @Nonnull List<NpcRuntimeFixtureSpawner.SpawnedNpc> spawnedNpcs) {
        NpcRuntimeCleanupReport.Builder report = NpcRuntimeCleanupReport.builder();
        if (spawnedNpcs.isEmpty()) {
            for (NpcRuntimeFixtureRegistry.FixtureRecord fixture : fixtureRegistry.fixtures()) {
                report.unresolvedFixture(fixture.fixtureId());
            }
            return report.build();
        }
        for (NpcRuntimeFixtureSpawner.SpawnedNpc spawnedNpc : spawnedNpcs) {
            try {
                executeWorldStep(world, () -> {
                    boolean removed = fixtureSpawner.cleanup(world.getEntityStore().getStore(), spawnedNpc);
                    if (!removed) {
                        throw new IllegalStateException("fixture cleanup returned false");
                    }
                    return NpcRuntimeTickScheduler.TickOutcome.continueRunning();
                });
                report.entityRemoval(true);
            } catch (Exception exception) {
                report.entityRemoval(false);
                report.unresolvedFixture(spawnedNpc.fixtureId());
            }
        }
        return report.build();
    }

    @Nonnull
    private NpcRuntimeArena ensureArenaChunkLoaded(@Nonnull World world, @Nonnull NpcRuntimeRequest request) throws Exception {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(
                coordinate(request.fixtures().npcUnderTest().position(), 0, "fixtures.npcUnderTest.position"),
                coordinate(request.fixtures().npcUnderTest().position(), 2, "fixtures.npcUnderTest.position")
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
        return NpcRuntimeArena.defaultArena(world.getName(), request.world().arena(), chunkIndex);
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

    private static boolean isAssertionEvidence(@Nonnull NpcRuntimeTraceRecord record) {
        Object kind = record.fields().get("kind");
        return "sensor-evidence".equals(kind)
                || "action-evidence".equals(kind)
                || "combat-evaluator-evidence".equals(kind);
    }

    private static final class SpawnedFixtureHolder {
        private final List<NpcRuntimeFixtureSpawner.SpawnedNpc> spawnedNpcs = new ArrayList<>();
        private final List<java.util.Map<String, Object>> evidenceRecords = new ArrayList<>();
        @Nullable
        private NpcRuntimeObservedNpc previousObserved;

        @Nullable
        private NpcRuntimeFixtureSpawner.SpawnedNpc npcUnderTest() {
            return spawnedNpcs.stream()
                    .filter(spawned -> "npcUnderTest".equals(spawned.fixtureId()))
                    .findFirst()
                    .orElse(null);
        }
    }
}
