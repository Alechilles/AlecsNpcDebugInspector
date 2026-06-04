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
import java.util.Map;
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
    private final NpcRuntimeTameworkFixtureMutator tameworkFixtureMutator;

    public NpcRuntimeLiveScenarioRunner(@Nonnull NpcRuntimeHarnessConfig config,
                                        @Nonnull NpcDebugSnapshotService snapshotService) {
        this(config, new NpcRuntimeFlatworldManager(), new NpcRuntimeFixtureSpawner(), snapshotService, new NpcRuntimeTickScheduler(), new NpcRuntimeObserver(), new NpcRuntimeTameworkFixtureMutator());
    }

    NpcRuntimeLiveScenarioRunner(@Nonnull NpcRuntimeHarnessConfig config,
                                 @Nonnull NpcRuntimeFlatworldManager flatworldManager,
                                 @Nonnull NpcRuntimeFixtureSpawner fixtureSpawner,
                                 @Nonnull NpcDebugSnapshotService snapshotService,
                                 @Nonnull NpcRuntimeTickScheduler tickScheduler,
                                 @Nonnull NpcRuntimeObserver observer,
                                 @Nonnull NpcRuntimeTameworkFixtureMutator tameworkFixtureMutator) {
        this.config = config;
        this.flatworldManager = flatworldManager;
        this.fixtureSpawner = fixtureSpawner;
        this.snapshotService = snapshotService;
        this.tickScheduler = tickScheduler;
        this.observer = observer;
        this.tameworkFixtureMutator = tameworkFixtureMutator;
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
        List<NpcRuntimeAssertion> assertions = NpcRuntimeAssertion.fromSpecs(request.assertions());
        NpcRuntimeFixtureRegistry fixtureRegistry = new NpcRuntimeFixtureRegistry();
        SpawnedFixtureHolder spawnedFixtures = new SpawnedFixtureHolder();

        try (NpcRuntimeTraceWriter writer = NpcRuntimeTraceWriter.open(tracePath, cadence.maxTraceBytes())) {
            writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), 0, "run-start")
                    .with("assetId", request.assetId())
                    .with("roleId", request.roleId())
                    .with("ticksRequested", request.ticks())
                    .with("profile", cadence.profile()));

            NpcRuntimeTickScheduler.RunSummary summary = tickScheduler.run(
                    run,
                    step -> executeWorldStep(world, step),
                    tick -> runOneTick(request, world, writer, run, cadence, fixtureRegistry, spawnedFixtures, arena, assertions, tick)
            );

            NpcRuntimeCleanupReport cleanupReport = cleanupOnWorldThread(world, fixtureRegistry, spawnedFixtures.spawnedNpcs);
            spawnedFixtures.spawnedNpcs.clear();
            run.markCleanup(cleanupReport.succeeded(), cleanupReport.message());
            writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "cleanup")
                    .with("attempted", run.cleanupAttempted())
                    .with("succeeded", run.cleanupSucceeded())
                    .with("message", run.cleanupMessage())
                    .with("report", cleanupReport.toMap()));
            if (!cleanupReport.succeeded()) {
                writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                        .with("status", "failed")
                        .with("classification", "cleanup-failed")
                        .with("error", cleanupReport.message())
                        .with("ticksRun", summary.ticksRun())
                        .with("profile", cadence.profile())
                        .with("traceBytesWritten", writer.bytesWritten()));
                return NpcRuntimeResult.cleanupFailed(request, summary.ticksRun(), tracePath, NpcRuntimeResult.Summary.empty(), cleanupReport);
            }

            if (summary.canceled()) {
                writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                        .with("status", "canceled")
                        .with("reason", summary.cancelReason())
                        .with("ticksRun", summary.ticksRun())
                        .with("profile", cadence.profile())
                        .with("traceBytesWritten", writer.bytesWritten()));
                return NpcRuntimeResult.canceled(
                        request.requestId(),
                        request.ticks(),
                        summary.ticksRun(),
                        summary.cancelReason() != null ? summary.cancelReason() : "canceled",
                        NpcRuntimeResult.Cleanup.fromReport(cleanupReport)
                );
            }

            List<NpcRuntimeAssertionResult> assertionResults = assertions.stream()
                    .map(assertion -> assertion.evaluate(spawnedFixtures.evidenceRecords))
                    .toList();
            NpcRuntimeResult.Summary resultSummary = NpcRuntimeResult.Summary.empty().withAssertions(assertionResults);
            if (!assertionResults.isEmpty()) {
                writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "assertions")
                        .with("results", assertionResults.stream().map(NpcRuntimeAssertionResult::toMap).toList()));
            }
            if (resultSummary.hasAssertionFailures() || resultSummary.hasAssertionUnknowns()) {
                String classification = resultSummary.hasAssertionFailures() ? "assertion-failed" : "assertion-unknown";
                writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                        .with("status", "failed")
                        .with("classification", classification)
                        .with("ticksRun", summary.ticksRun())
                        .with("profile", cadence.profile())
                        .with("traceBytesWritten", writer.bytesWritten()));
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

            writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), summary.ticksRun(), "run-end")
                    .with("status", "passed")
                    .with("ticksRun", summary.ticksRun())
                    .with("mode", summary.completedEarly() ? "assertion-driven" : "tick-driven")
                    .with("completionReason", summary.completionReason())
                    .with("profile", cadence.profile())
                    .with("traceBytesWritten", writer.bytesWritten()));
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
                                                           @Nonnull List<NpcRuntimeAssertion> assertions,
                                                           int tick) throws Exception {
        Store<EntityStore> store = world.getEntityStore().getStore();

        if (tick == 0) {
            writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), tick, "world-ready")
                    .with("world", world.getName())
                    .with("ticking", world.isTicking())
                    .with("paused", world.isPaused())
                    .with("playerCount", world.getPlayerCount())
                    .with("chunkResidency", arena.residencyMode())
                    .with("spawnChunkIndex", arena.spawnChunkIndex()));
            writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), tick, "arena-reset")
                    .with("arena", request.world().arena())
                    .with("mode", "no-block-reset-yet")
                    .with("details", arena.toMap()));
            writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), tick, "multi-npc-setup")
                    .with("mode", request.multiNpc().mode())
                    .with("fixtureCount", request.fixtures().list().size())
                    .with("linkedFixtureCount", linkedFixtureCount(request.fixtures().list()))
                    .with("deliveryWindowTicks", request.multiNpc().deliveryWindowTicks())
                    .with("maxFixtureCount", request.multiNpc().maxFixtureCount())
                    .with("unsupportedRelationshipFields", List.of("engineFlockMembershipMutation", "engineFamilyBindingMutation", "engineMessageBusMutation", "engineBeaconMutation")));
            for (NpcRuntimeFixtureSpec fixture : request.fixtures().list()) {
                NpcRuntimeFixtureSpawner.SpawnedNpc spawned = fixtureSpawner.spawnFixture(world, fixture);
                spawnedFixtures.spawnedNpcs.add(spawned);
                if (spawned.uuid() != null) {
                    run.addNpc(fixture.fixtureId(), spawned.uuid());
                }
                fixtureRegistry.recordEntity(fixture.fixtureId(), fixture.kind().jsonName(), spawned.uuid());
                run.addFixture(fixture.fixtureId());
                writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), tick, "fixture-spawn")
                        .with("fixture", fixture.fixtureId())
                        .with("fixtureKind", fixture.kind().jsonName())
                        .with("roleId", fixture.roleId())
                        .with("targetSlot", fixture.targetSlot())
                        .with("flockId", fixture.flockId())
                        .with("flockRole", fixture.flockRole())
                        .with("familyId", fixture.familyId())
                        .with("familyRole", fixture.familyRole())
                        .with("leaderFixtureId", fixture.leaderFixtureId())
                        .with("parentFixtureId", fixture.parentFixtureId())
                        .with("npcUuid", spawned.uuid() != null ? spawned.uuid().toString() : null)
                        .with("result", spawned.toSpawnResult().toMap()));
                writeFixtureLinkIfPresent(request, writer, cadence, fixtureRegistry, spawnedFixtures.evidenceRecords, tick, fixture, "flockLeader", fixture.leaderFixtureId());
                writeFixtureLinkIfPresent(request, writer, cadence, fixtureRegistry, spawnedFixtures.evidenceRecords, tick, fixture, "parent", fixture.parentFixtureId());
                for (NpcRuntimeTraceRecord record : tameworkFixtureMutator.apply(request.requestId(), tick, store, spawned, fixture.tamework())) {
                    writeEventIfEnabled(writer, cadence, record);
                    if (tick >= request.timing().warmupTicks()) {
                        spawnedFixtures.evidenceRecords.add(record.fields());
                    }
                }
            }
        }

        writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), tick, "tick-start"));

        NpcRuntimeFixtureSpawner.SpawnedNpc npcUnderTest = spawnedFixtures.npcUnderTest();
        if (cadence.shouldRecordSnapshot(tick) && npcUnderTest != null) {
            NpcDebugSnapshot snapshot = snapshotService.capture(npcUnderTest.uuid(), npcUnderTest.ref(), store);
            writer.write(NpcRuntimeTraceRecord.of(request.requestId(), tick, "npc-snapshot")
                    .with("npcUuid", npcUnderTest.uuid() != null ? npcUnderTest.uuid().toString() : null)
                    .with("title", snapshot.title())
                    .with("subtitle", snapshot.subtitle())
                    .with("details", snapshot.details()));
            NpcRuntimeObservedNpc observed = observer.observe(npcUnderTest.uuid(), snapshot);
            for (NpcRuntimeTraceRecord record : observer.traceRecords(
                    request.requestId(),
                    tick,
                    observed,
                    spawnedFixtures.previousObserved,
                    request.engineHooks(),
                    npcUnderTest.fixtureId()
            )) {
                writeEventIfEnabled(writer, cadence, record);
                if (isAssertionEvidence(record) && tick >= request.timing().warmupTicks()) {
                    spawnedFixtures.evidenceRecords.add(record.fields());
                }
            }
            spawnedFixtures.previousObserved = observed;
        }

        if (shouldStopAfterAssertionsResolve(request, assertions, spawnedFixtures.evidenceRecords, tick)) {
            List<NpcRuntimeAssertionResult> currentResults = assertions.stream()
                    .map(assertion -> assertion.evaluate(spawnedFixtures.evidenceRecords))
                    .toList();
            writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), tick, "assertions-resolved")
                    .with("results", currentResults.stream().map(NpcRuntimeAssertionResult::toMap).toList()));
            return NpcRuntimeTickScheduler.TickOutcome.completed("assertions resolved");
        }

        writeEventIfEnabled(writer, cadence, NpcRuntimeTraceRecord.of(request.requestId(), tick, "tick-end"));

        return run.canceled()
                ? NpcRuntimeTickScheduler.TickOutcome.canceled(run.cancelReason() != null ? run.cancelReason() : "canceled")
                : NpcRuntimeTickScheduler.TickOutcome.continueRunning();
    }

    private void writeFixtureLinkIfPresent(@Nonnull NpcRuntimeRequest request,
                                           @Nonnull NpcRuntimeTraceWriter writer,
                                           @Nonnull NpcRuntimeObservationCadence cadence,
                                           @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry,
                                           @Nonnull List<Map<String, Object>> evidenceRecords,
                                           int tick,
                                           @Nonnull NpcRuntimeFixtureSpec fixture,
                                           @Nonnull String relationship,
                                           @Nullable String targetFixtureId) throws java.io.IOException {
        if (targetFixtureId == null || targetFixtureId.isBlank()) {
            return;
        }
        NpcRuntimeFixtureRegistry.FixtureLink link = fixtureRegistry.recordFixtureLink(fixture.fixtureId(), relationship, targetFixtureId);
        NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(request.requestId(), tick, "fixture-link")
                .with("fixture", link.fixtureId())
                .with("fixtureId", link.fixtureId())
                .with("relationship", link.relationship())
                .with("targetFixtureId", link.targetFixtureId())
                .with("flockId", fixture.flockId())
                .with("flockRole", fixture.flockRole())
                .with("familyId", fixture.familyId())
                .with("familyRole", fixture.familyRole())
                .with("engineApplied", false)
                .with("unsupportedFields", List.of("engineFlockMembershipMutation", "engineFamilyBindingMutation"));
        writeEventIfEnabled(writer, cadence, record);
        evidenceRecords.add(record.fields());
        NpcRuntimeTraceRecord evidence = flockEvidenceRecord(request, tick, fixture, relationship, targetFixtureId);
        writeEventIfEnabled(writer, cadence, evidence);
        evidenceRecords.add(evidence.fields());
    }

    @Nonnull
    private NpcRuntimeTraceRecord flockEvidenceRecord(@Nonnull NpcRuntimeRequest request,
                                                      int tick,
                                                      @Nonnull NpcRuntimeFixtureSpec fixture,
                                                      @Nonnull String relationship,
                                                      @Nonnull String targetFixtureId) {
        NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(request.requestId(), tick, "flock-evidence")
                .with("fixtureId", fixture.fixtureId())
                .with("roleId", fixture.roleId())
                .with("flockId", fixture.flockId())
                .with("flockRole", fixture.flockRole())
                .with("familyId", fixture.familyId())
                .with("familyRole", fixture.familyRole())
                .with("unsupportedFields", unsupportedRelationshipFields(relationship));
        if ("flockLeader".equals(relationship)) {
            record.with("leaderFixtureId", targetFixtureId)
                    .with("memberCount", linkedMemberCount(request.fixtures().list(), targetFixtureId));
        } else if ("parent".equals(relationship)) {
            record.with("parentFixtureId", targetFixtureId)
                    .with("childCount", linkedChildCount(request.fixtures().list(), targetFixtureId));
        }
        return record;
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
                || "action-start".equals(kind)
                || "action-change".equals(kind)
                || "action-end".equals(kind)
                || "combat-evaluator-evidence".equals(kind)
                || "tamework-evidence".equals(kind)
                || "tamework-fixture-mutation".equals(kind)
                || "fixture-link".equals(kind)
                || "flock-evidence".equals(kind)
                || "message-evidence".equals(kind)
                || "beacon-evidence".equals(kind)
                || "target-selection-evidence".equals(kind)
                || "pathing-evidence".equals(kind)
                || "combat-eligibility-evidence".equals(kind)
                || "instruction-lifecycle-evidence".equals(kind);
    }

    private static long linkedFixtureCount(@Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        return fixtures.stream()
                .filter(fixture -> fixture.leaderFixtureId() != null
                        || fixture.parentFixtureId() != null)
                .count();
    }

    private static int linkedMemberCount(@Nonnull List<NpcRuntimeFixtureSpec> fixtures, @Nonnull String leaderFixtureId) {
        long followers = fixtures.stream()
                .filter(fixture -> leaderFixtureId.equals(fixture.leaderFixtureId()))
                .count();
        return Math.toIntExact(followers + 1);
    }

    private static int linkedChildCount(@Nonnull List<NpcRuntimeFixtureSpec> fixtures, @Nonnull String parentFixtureId) {
        long children = fixtures.stream()
                .filter(fixture -> parentFixtureId.equals(fixture.parentFixtureId()))
                .count();
        return Math.toIntExact(children);
    }

    @Nonnull
    private static List<String> unsupportedRelationshipFields(@Nonnull String relationship) {
        if ("flockLeader".equals(relationship)) {
            return List.of("engineFlockMembershipMutation");
        }
        if ("parent".equals(relationship)) {
            return List.of("engineFamilyBindingMutation");
        }
        return List.of("engineRelationshipMutation");
    }

    private static boolean shouldStopAfterAssertionsResolve(@Nonnull NpcRuntimeRequest request,
                                                            @Nonnull List<NpcRuntimeAssertion> assertions,
                                                            @Nonnull List<Map<String, Object>> evidenceRecords,
                                                            int tick) {
        if (!request.timing().stopWhenAssertionsResolved() || assertions.isEmpty() || tick < request.timing().warmupTicks()) {
            return false;
        }
        if (!assertions.stream().allMatch(NpcRuntimeAssertion::canResolveBeforeEnd)) {
            return false;
        }
        List<NpcRuntimeAssertionResult> currentResults = assertions.stream()
                .map(assertion -> assertion.evaluate(evidenceRecords))
                .toList();
        return currentResults.stream().allMatch(result -> "passed".equals(result.status()));
    }

    private static void writeEventIfEnabled(@Nonnull NpcRuntimeTraceWriter writer,
                                            @Nonnull NpcRuntimeObservationCadence cadence,
                                            @Nonnull NpcRuntimeTraceRecord record) throws java.io.IOException {
        Object kind = record.fields().get("kind");
        Object tick = record.fields().get("tick");
        if (kind instanceof String kindText && tick instanceof Number tickNumber
                && cadence.shouldRecordEvent(kindText, tickNumber.intValue())) {
            writer.write(record);
        }
    }

    private static final class SpawnedFixtureHolder {
        private final List<NpcRuntimeFixtureSpawner.SpawnedNpc> spawnedNpcs = new ArrayList<>();
        private final List<Map<String, Object>> evidenceRecords = new ArrayList<>();
        @Nullable
        private NpcRuntimeObservedNpc previousObserved;

        @Nullable
        private NpcRuntimeFixtureSpawner.SpawnedNpc npcUnderTest() {
            return spawnedNpcs.stream()
                    .filter(spawned -> spawned.kind() == NpcRuntimeFixtureKind.NPC_UNDER_TEST)
                    .findFirst()
                    .orElse(null);
        }
    }
}
