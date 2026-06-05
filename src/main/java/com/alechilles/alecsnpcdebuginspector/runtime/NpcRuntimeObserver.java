package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Emits structured runtime observation records from inspector snapshots.
 */
public final class NpcRuntimeObserver {
    private final NpcRuntimeSensorObserver sensorObserver = new NpcRuntimeSensorObserver();
    private final NpcRuntimeActionObserver actionObserver = new NpcRuntimeActionObserver();
    private final NpcRuntimeTameworkObserver tameworkObserver = new NpcRuntimeTameworkObserver();
    private final NpcRuntimeEngineHookObserver engineHookObserver = new NpcRuntimeEngineHookObserver();

    @Nonnull
    public NpcRuntimeObservedNpc observe(@Nullable UUID npcUuid, @Nonnull NpcDebugSnapshot snapshot) {
        return NpcRuntimeObservedNpc.fromSnapshot(npcUuid, snapshot);
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous) {
        return traceRecords(
                requestId,
                tick,
                current,
                previous,
                new NpcRuntimeRequest.EngineHooksSpec(false, false, false, false),
                current.npcUuid() != null ? current.npcUuid().toString() : "npc_under_test"
        );
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId) {
        return traceRecords(requestId, tick, current, previous, engineHooks, npcId, new NpcRuntimeActionObserver.ActionLifecycleTracker());
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeActionObserver.ActionLifecycleTracker actionLifecycleTracker) {
        return traceRecords(requestId, tick, current, previous, engineHooks, npcId, actionLifecycleTracker, List.of());
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeActionObserver.ActionLifecycleTracker actionLifecycleTracker,
                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        return traceRecords(requestId, tick, current, previous, engineHooks, npcId, actionLifecycleTracker, fixtures, new NpcRuntimeFixtureRegistry());
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeActionObserver.ActionLifecycleTracker actionLifecycleTracker,
                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                                    @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry) {
        Map<String, NpcRuntimeObservedNpc> currentByFixture = Map.of(npcId, current);
        Map<String, NpcRuntimeObservedNpc> previousByFixture = previous != null ? Map.of(npcId, previous) : Map.of();
        return traceRecords(
                requestId,
                tick,
                current,
                previous,
                engineHooks,
                npcId,
                actionLifecycleTracker,
                fixtures,
                fixtureRegistry,
                currentByFixture,
                previousByFixture
        );
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeActionObserver.ActionLifecycleTracker actionLifecycleTracker,
                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                                    @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry,
                                                    @Nonnull Map<String, NpcRuntimeObservedNpc> currentByFixture,
                                                    @Nonnull Map<String, NpcRuntimeObservedNpc> previousByFixture) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        records.add(record(requestId, tick, "npc-state", current.stateMap()));
        addSection(records, requestId, tick, "targeting", current.section("Targeting / Sensors"));
        addSection(records, requestId, tick, "timers", current.section("Timers / Cooldowns"));
        addSection(records, requestId, tick, "pathing", current.section("Pathing"));
        addSection(records, requestId, tick, "combat", current.section("Combat"));
        addSection(records, requestId, tick, "inventory", current.section("Inventory / Equipment"));
        addSection(records, requestId, tick, "alarms", current.section("Alarms"));
        addSection(records, requestId, tick, "flags", current.section("Flags"));
        addSection(records, requestId, tick, "components", current.section("Components"));
        addSection(records, requestId, tick, "flock", current.section("Flock"));
        records.addAll(flockEvidenceRecords(requestId, tick, current, previous, fixtures, currentByFixture, previousByFixture));
        records.addAll(sensorObserver.traceRecords(requestId, tick, current, fixtures));
        records.addAll(actionObserver.traceRecords(requestId, tick, current, previous, actionLifecycleTracker));
        Map<String, Object> tamework = current.tameworkMap();
        if (!tamework.isEmpty()) {
            records.add(record(requestId, tick, "tamework", tamework));
            records.addAll(tameworkObserver.traceRecords(requestId, tick, tamework));
        }
        records.addAll(engineHookObserver.traceRecords(requestId, tick, npcId, current, previous, engineHooks, fixtureRegistry, fixtures));

        NpcRuntimeObservationDiff diff = NpcRuntimeObservationDiff.between(previous, current);
        if (diff.changed()) {
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "npc-transition")
                    .with("changeCount", diff.changes().size())
                    .with("changes", diff.changes().stream().map(NpcRuntimeObserver::changeMap).toList()));
        }
        return records;
    }

    @Nonnull
    private static List<NpcRuntimeTraceRecord> flockEvidenceRecords(@Nonnull String requestId,
                                                                    int tick,
                                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                                                    @Nonnull Map<String, NpcRuntimeObservedNpc> currentByFixture,
                                                                    @Nonnull Map<String, NpcRuntimeObservedNpc> previousByFixture) {
        List<NpcRuntimeFixtureSpec> npcFixtures = fixtures.stream()
                .filter(fixture -> fixture.kind().entityLike())
                .toList();
        if (npcFixtures.isEmpty()) {
            return List.of();
        }
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        Map<String, NpcRuntimeFixtureSpec> fixtureById = new LinkedHashMap<>();
        for (NpcRuntimeFixtureSpec fixture : fixtures) {
            fixtureById.put(fixture.fixtureId(), fixture);
        }
        for (NpcRuntimeFixtureSpec fixture : npcFixtures) {
            NpcRuntimeObservedNpc observed = currentByFixture.getOrDefault(fixture.fixtureId(), current);
            records.add(flockEvidenceRecord(requestId, tick, observed, fixture, npcFixtures, fixtureById));
        }
        for (NpcRuntimeFixtureSpec fixture : npcFixtures) {
            NpcRuntimeObservedNpc observed = currentByFixture.getOrDefault(fixture.fixtureId(), current);
            NpcRuntimeObservedNpc previousObserved = previousByFixture.get(fixture.fixtureId());
            if (previousObserved == null && fixture.fixtureId().equals("npcUnderTest")) {
                previousObserved = previous;
            }
            List<Map<String, Object>> flockChanges = flockChanges(previousObserved, observed);
            if (!flockChanges.isEmpty()) {
                records.add(NpcRuntimeTraceRecord.of(requestId, tick, "flock-transition")
                        .with("fixtureId", fixture.fixtureId())
                        .with("roleId", fixture.roleId())
                        .with("flockId", fixture.flockId())
                        .with("leaderFixtureId", fixture.leaderFixtureId())
                        .with("familyId", fixture.familyId())
                        .with("parentFixtureId", fixture.parentFixtureId())
                        .with("changeCount", flockChanges.size())
                        .with("changes", flockChanges)
                        .with("unsupportedFields", unsupportedFlockFields(fixture)));
            }
        }
        return records;
    }

    @Nonnull
    private static NpcRuntimeTraceRecord flockEvidenceRecord(@Nonnull String requestId,
                                                             int tick,
                                                             @Nonnull NpcRuntimeObservedNpc current,
                                                             @Nonnull NpcRuntimeFixtureSpec fixture,
                                                             @Nonnull List<NpcRuntimeFixtureSpec> npcFixtures,
                                                             @Nonnull Map<String, NpcRuntimeFixtureSpec> fixtureById) {
        boolean leader = isLeader(fixture, npcFixtures);
        String leaderFixtureId = leaderFixtureId(fixture, leader);
        String parentFixtureId = parentFixtureId(fixture);
        int memberCount = memberCount(fixture, leaderFixtureId, npcFixtures);
        int childCount = childCount(fixture, parentFixtureId, npcFixtures);
        List<String> missingFixtureIds = missingFixtureIds(fixture, fixtureById);
        NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(requestId, tick, "flock-evidence")
                .with("fixtureId", fixture.fixtureId())
                .with("roleId", fixture.roleId())
                .with("flockId", fixture.flockId())
                .with("flockRole", fixture.flockRole())
                .with("familyId", fixture.familyId())
                .with("familyRole", fixture.familyRole())
                .with("leaderFixtureId", leaderFixtureId)
                .with("parentFixtureId", parentFixtureId)
                .with("memberCount", memberCount)
                .with("childCount", childCount)
                .with("isLeader", leader)
                .with("distanceToLeader", distanceToFixture(fixture, leaderFixtureId, fixtureById))
                .with("distanceToParent", distanceToFixture(fixture, parentFixtureId, fixtureById))
                .with("state", current.aiState())
                .with("substate", current.aiSubstate())
                .with("currentInstruction", current.currentInstruction())
                .with("observedMemberCount", memberCount)
                .with("expectedMemberCount", memberCount)
                .with("missingFixtureIds", missingFixtureIds)
                .with("extraFixtureIds", List.of())
                .with("unsupportedFields", unsupportedFlockFields(fixture));
        return record;
    }

    @Nullable
    private static String leaderFixtureId(@Nonnull NpcRuntimeFixtureSpec fixture, boolean isLeader) {
        return fixture.leaderFixtureId() != null ? fixture.leaderFixtureId() : isLeader ? fixture.fixtureId() : null;
    }

    @Nullable
    private static String parentFixtureId(@Nonnull NpcRuntimeFixtureSpec fixture) {
        return fixture.parentFixtureId();
    }

    private static boolean isLeader(@Nonnull NpcRuntimeFixtureSpec fixture,
                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        return "leader".equalsIgnoreCase(String.valueOf(fixture.flockRole()))
                || fixtures.stream().anyMatch(candidate -> fixture.fixtureId().equals(candidate.leaderFixtureId()));
    }

    private static int memberCount(@Nonnull NpcRuntimeFixtureSpec fixture,
                                   @Nullable String leaderFixtureId,
                                   @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        String leader = leaderFixtureId != null ? leaderFixtureId : fixture.fixtureId();
        long linkedFollowers = fixtures.stream()
                .filter(candidate -> leader.equals(candidate.leaderFixtureId()))
                .count();
        boolean leaderFixturePresent = fixtures.stream().anyMatch(candidate -> leader.equals(candidate.fixtureId()));
        if (linkedFollowers > 0 || leaderFixturePresent) {
            return Math.toIntExact(linkedFollowers + (leaderFixturePresent ? 1 : 0));
        }
        return 1;
    }

    private static int childCount(@Nonnull NpcRuntimeFixtureSpec fixture,
                                  @Nullable String parentFixtureId,
                                  @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        String parent = parentFixtureId != null ? parentFixtureId : fixture.fixtureId();
        long linkedChildren = fixtures.stream()
                .filter(candidate -> parent.equals(candidate.parentFixtureId()))
                .count();
        return Math.toIntExact(linkedChildren);
    }

    @Nullable
    private static Double distanceToFixture(@Nonnull NpcRuntimeFixtureSpec fixture,
                                            @Nullable String targetFixtureId,
                                            @Nonnull Map<String, NpcRuntimeFixtureSpec> fixtureById) {
        if (targetFixtureId == null || targetFixtureId.equals(fixture.fixtureId())) {
            return null;
        }
        NpcRuntimeFixtureSpec target = fixtureById.get(targetFixtureId);
        if (target == null) {
            return null;
        }
        return distance(fixture.position(), target.position());
    }

    @Nullable
    private static Double distance(@Nonnull List<Object> first, @Nonnull List<Object> second) {
        if (first.size() != 3 || second.size() != 3) {
            return null;
        }
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            if (!(first.get(i) instanceof Number firstNumber) || !(second.get(i) instanceof Number secondNumber)) {
                return null;
            }
            double delta = firstNumber.doubleValue() - secondNumber.doubleValue();
            sum += delta * delta;
        }
        return Math.sqrt(sum);
    }

    @Nonnull
    private static List<String> unsupportedFlockFields(@Nonnull NpcRuntimeFixtureSpec fixture) {
        ArrayList<String> unsupported = new ArrayList<>();
        if (fixture.flockId() != null || fixture.flockRole() != null || fixture.leaderFixtureId() != null) {
            unsupported.add("enginePrivateFlockMembership");
        }
        if (fixture.familyId() != null || fixture.familyRole() != null || fixture.parentFixtureId() != null) {
            unsupported.add("enginePrivateFamilyBinding");
        }
        return List.copyOf(unsupported);
    }

    @Nonnull
    private static List<String> missingFixtureIds(@Nonnull NpcRuntimeFixtureSpec fixture,
                                                  @Nonnull Map<String, NpcRuntimeFixtureSpec> fixtureById) {
        ArrayList<String> missing = new ArrayList<>();
        addMissing(fixture.leaderFixtureId(), fixtureById, missing);
        addMissing(fixture.parentFixtureId(), fixtureById, missing);
        return List.copyOf(missing);
    }

    private static void addMissing(@Nullable String fixtureId,
                                   @Nonnull Map<String, NpcRuntimeFixtureSpec> fixtureById,
                                   @Nonnull List<String> missing) {
        if (fixtureId != null && !fixtureId.isBlank() && !fixtureById.containsKey(fixtureId) && !missing.contains(fixtureId)) {
            missing.add(fixtureId);
        }
    }

    @Nonnull
    private static List<Map<String, Object>> flockChanges(@Nullable NpcRuntimeObservedNpc previous,
                                                          @Nonnull NpcRuntimeObservedNpc current) {
        if (previous == null) {
            return List.of();
        }
        Map<String, String> before = previous.section("Flock");
        Map<String, String> after = current.section("Flock");
        ArrayList<Map<String, Object>> changes = new ArrayList<>();
        for (String field : unionKeys(before, after)) {
            Object beforeValue = before.get(field);
            Object afterValue = after.get(field);
            if (!java.util.Objects.equals(beforeValue, afterValue)) {
                LinkedHashMap<String, Object> change = new LinkedHashMap<>();
                change.put("section", "Flock");
                change.put("field", field);
                change.put("before", beforeValue);
                change.put("after", afterValue);
                changes.add(change);
            }
        }
        return List.copyOf(changes);
    }

    @Nonnull
    private static List<String> unionKeys(@Nonnull Map<String, String> first,
                                          @Nonnull Map<String, String> second) {
        ArrayList<String> keys = new ArrayList<>();
        for (String key : first.keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        for (String key : second.keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Nonnull
    private static Map<String, Object> changeMap(@Nonnull NpcRuntimeObservationDiff.Change change) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("section", change.section());
        map.put("field", change.field());
        map.put("before", change.before());
        map.put("after", change.after());
        return map;
    }

    private static void addSection(@Nonnull List<NpcRuntimeTraceRecord> records,
                                   @Nonnull String requestId,
                                   int tick,
                                   @Nonnull String eventKind,
                                   @Nonnull Map<String, String> values) {
        if (!values.isEmpty()) {
            records.add(record(requestId, tick, eventKind, Map.of("fields", values)));
        }
    }

    @Nonnull
    private static NpcRuntimeTraceRecord record(@Nonnull String requestId,
                                                int tick,
                                                @Nonnull String eventKind,
                                                @Nonnull Map<String, ?> values) {
        NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(requestId, tick, eventKind);
        values.forEach(record::with);
        return record;
    }
}
