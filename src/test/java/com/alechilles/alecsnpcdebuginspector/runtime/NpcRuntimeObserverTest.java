package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeObserverTest {
    @Test
    void emitsStructuredSectionRecordsFromSnapshotDetails() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc observed = observer.observe(null, sampleSnapshot("Idle"));

        List<NpcRuntimeTraceRecord> records = observer.traceRecords("request-a", 3, observed, null);

        assertTrue(records.stream().anyMatch(record -> "npc-state".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "targeting".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "timers".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "pathing".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "combat".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "components".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "flock".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "tamework".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "sensor-evidence".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "action-evidence".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "combat-evaluator-evidence".equals(record.fields().get("kind"))));
        assertTrue(records.stream().anyMatch(record -> "tamework-evidence".equals(record.fields().get("kind"))));
        assertEquals("Idle", observed.section("AI").get("state"));
        assertEquals("false", observed.section("Pathing").get("followingPath"));
    }

    @Test
    void emitsTransitionRecordsWhenObservedFieldsChange() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc previous = observer.observe(null, sampleSnapshot("Idle"));
        NpcRuntimeObservedNpc current = observer.observe(null, sampleSnapshot("Attack"));

        List<NpcRuntimeTraceRecord> records = observer.traceRecords("request-a", 4, current, previous);

        NpcRuntimeTraceRecord transition = records.stream()
                .filter(record -> "npc-transition".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
        assertEquals(2, transition.fields().get("changeCount"));
        assertTrue(transition.toJson().contains("\"field\":\"state\""));
        assertTrue(transition.toJson().contains("\"after\":\"Attack\""));
    }

    @Test
    void emitsFlockEvidenceForEveryNpcFixtureWithDistancesAndState() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc observed = observer.observe(null, sampleSnapshot("Idle"));
        List<NpcRuntimeFixtureSpec> fixtures = List.of(
                fixture(Map.of(
                        "fixtureId", "npcUnderTest",
                        "kind", "npcUnderTest",
                        "roleId", "LeaderRole",
                        "position", List.of(0, 64, 0),
                        "flockId", "generated.flock",
                        "flockRole", "leader",
                        "familyId", "generated.family",
                        "familyRole", "adult"
                )),
                fixture(Map.of(
                        "fixtureId", "flock.follower",
                        "kind", "flockMember",
                        "roleId", "FollowerRole",
                        "position", List.of(3, 64, 4),
                        "flockId", "generated.flock",
                        "flockRole", "follower",
                        "leaderFixtureId", "npcUnderTest"
                )),
                fixture(Map.of(
                        "fixtureId", "family.child",
                        "kind", "familyMember",
                        "roleId", "ChildRole",
                        "position", List.of(0, 64, 6),
                        "familyId", "generated.family",
                        "familyRole", "child",
                        "parentFixtureId", "npcUnderTest"
                ))
        );

        List<NpcRuntimeTraceRecord> records = observer.traceRecords(
                "request-a",
                6,
                observed,
                null,
                new NpcRuntimeRequest.EngineHooksSpec(false, false, false, false),
                "npcUnderTest",
                new NpcRuntimeActionObserver.ActionLifecycleTracker(),
                fixtures
        );
        List<NpcRuntimeTraceRecord> flockEvidence = records.stream()
                .filter(record -> "flock-evidence".equals(record.fields().get("kind")))
                .toList();

        assertEquals(3, flockEvidence.size());
        NpcRuntimeTraceRecord leader = flockEvidence.stream()
                .filter(record -> "npcUnderTest".equals(record.fields().get("fixtureId")))
                .findFirst()
                .orElseThrow();
        assertEquals(true, leader.fields().get("isLeader"));
        assertEquals(2, leader.fields().get("memberCount"));
        assertEquals(1, leader.fields().get("childCount"));
        assertEquals("Idle", leader.fields().get("state"));
        assertEquals("start", leader.fields().get("substate"));
        assertEquals("IdleLoop / IdleBody", leader.fields().get("currentInstruction"));
        assertEquals(List.of(), leader.fields().get("missingFixtureIds"));

        NpcRuntimeTraceRecord follower = flockEvidence.stream()
                .filter(record -> "flock.follower".equals(record.fields().get("fixtureId")))
                .findFirst()
                .orElseThrow();
        assertEquals("npcUnderTest", follower.fields().get("leaderFixtureId"));
        assertEquals(5.0, (Double) follower.fields().get("distanceToLeader"), 0.001);
        assertTrue(follower.fields().get("unsupportedFields").toString().contains("enginePrivateFlockMembership"));

        NpcRuntimeTraceRecord child = flockEvidence.stream()
                .filter(record -> "family.child".equals(record.fields().get("fixtureId")))
                .findFirst()
                .orElseThrow();
        assertEquals("npcUnderTest", child.fields().get("parentFixtureId"));
        assertEquals(6.0, (Double) child.fields().get("distanceToParent"), 0.001);
        assertTrue(child.fields().get("unsupportedFields").toString().contains("enginePrivateFamilyBinding"));
    }

    @Test
    void emitsMissingLeaderEvidenceWhenFixtureReferenceCannotBeResolved() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc observed = observer.observe(null, sampleSnapshot("Idle"));
        List<NpcRuntimeFixtureSpec> fixtures = List.of(
                fixture(Map.of(
                        "fixtureId", "flock.orphan",
                        "kind", "flockMember",
                        "roleId", "FollowerRole",
                        "position", List.of(3, 64, 4),
                        "flockId", "generated.flock",
                        "flockRole", "follower",
                        "leaderFixtureId", "flock.missing"
                ))
        );

        NpcRuntimeTraceRecord evidence = observer.traceRecords(
                        "request-a",
                        6,
                        observed,
                        null,
                        new NpcRuntimeRequest.EngineHooksSpec(false, false, false, false),
                        "flock.orphan",
                        new NpcRuntimeActionObserver.ActionLifecycleTracker(),
                        fixtures
                ).stream()
                .filter(record -> "flock-evidence".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();

        assertEquals("flock.missing", evidence.fields().get("leaderFixtureId"));
        assertEquals(List.of("flock.missing"), evidence.fields().get("missingFixtureIds"));
        assertEquals(null, evidence.fields().get("distanceToLeader"));
    }

    @Test
    void emitsFlockTransitionWhenFlockSnapshotSectionChanges() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc previous = observer.observe(null, sampleSnapshot("Idle", "none"));
        NpcRuntimeObservedNpc current = observer.observe(null, sampleSnapshot("Idle", "leader=npcUnderTest"));
        List<NpcRuntimeFixtureSpec> fixtures = List.of(
                fixture(Map.of(
                        "fixtureId", "npcUnderTest",
                        "kind", "npcUnderTest",
                        "roleId", "LeaderRole",
                        "position", List.of(0, 64, 0),
                        "flockId", "generated.flock",
                        "flockRole", "leader"
                ))
        );

        NpcRuntimeTraceRecord transition = observer.traceRecords(
                        "request-a",
                        7,
                        current,
                        previous,
                        new NpcRuntimeRequest.EngineHooksSpec(false, false, false, false),
                        "npcUnderTest",
                        new NpcRuntimeActionObserver.ActionLifecycleTracker(),
                        fixtures
                ).stream()
                .filter(record -> "flock-transition".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();

        assertEquals("npcUnderTest", transition.fields().get("fixtureId"));
        assertEquals(1, transition.fields().get("changeCount"));
        assertTrue(transition.toJson().contains("\"field\":\"membership\""));
        assertTrue(transition.toJson().contains("leader=npcUnderTest"));
    }

    private static NpcDebugSnapshot sampleSnapshot(String state) {
        return sampleSnapshot(state, "none");
    }

    private static NpcDebugSnapshot sampleSnapshot(String state, String flockMembership) {
        return new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Overview ===
                        - UUID: sample
                        - Role Id: Mob_Test
                        - State: %s

                        === Tamework ===
                        - Plugin Loaded: true
                        - Tamed Component: false

                        === AI ===
                        - State: %s
                        - Sub-State: start
                        - Current Tree Step: IdleLoop
                        - Current Body Step: IdleBody
                        - Transition Actions Running: false

                        === Targeting / Sensors ===
                        - Target LockedTarget: <none>
                        - Sensor Scope Keys: 2

                        === Pathing ===
                        - Following Path: false
                        - Nav State: AT_GOAL

                        === Timers / Cooldowns ===
                        - Attack Executing: false
                        - Scope Timer Keys: 1

                        === Combat ===
                        - Executing Attack: false
                        - Attack Override Count: 0

                        === Components ===
                        - NPCEntity: true
                        - Inventory: true

                        === Flock ===
                        - Membership: %s
                        """.formatted(state, state, flockMembership)
        );
    }

    private static NpcRuntimeFixtureSpec fixture(Map<String, Object> fields) {
        return NpcRuntimeFixtureSpec.fromMap(fields, "observer_fixture", "fixtures.list[]", "FallbackRole");
    }
}
