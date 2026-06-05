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

    @Test
    void emitsMessageAndBeaconEvidenceFromObservableSnapshotSections() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc observed = observer.observe(null, signalSnapshot(true, true));
        List<NpcRuntimeFixtureSpec> fixtures = signalFixtures();

        List<NpcRuntimeTraceRecord> records = observer.traceRecords(
                "request-a",
                72,
                observed,
                null,
                new NpcRuntimeRequest.EngineHooksSpec(false, false, false, false),
                "npcUnderTest",
                new NpcRuntimeActionObserver.ActionLifecycleTracker(),
                fixtures
        );

        NpcRuntimeTraceRecord surface = records.stream()
                .filter(record -> "signal-surface".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
        assertTrue(surface.fields().get("unsupportedFields").toString().contains("enginePrivateMessageQueue"));
        assertTrue(surface.fields().get("messageSourceSections").toString().contains("Recent Events"));

        NpcRuntimeTraceRecord message = records.stream()
                .filter(record -> "message-evidence".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
        assertEquals("message.threat", message.fields().get("fixtureId"));
        assertEquals("threat.broadcast", message.fields().get("messageType"));
        assertEquals("npcUnderTest", message.fields().get("senderFixtureId"));
        assertEquals("flock.follower_one", message.fields().get("receiverFixtureId"));
        assertEquals("target.Enemy", message.fields().get("targetFixtureId"));
        assertEquals("Enemy", message.fields().get("targetSlot"));
        assertEquals(List.of(), message.fields().get("unsupportedFields"));
        assertTrue(message.fields().get("observedValue").toString().contains("threat.broadcast"));

        NpcRuntimeTraceRecord beacon = records.stream()
                .filter(record -> "beacon-evidence".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
        assertEquals("beacon.threat", beacon.fields().get("fixtureId"));
        assertEquals("threat", beacon.fields().get("beaconType"));
        assertEquals("npcUnderTest", beacon.fields().get("sourceFixtureId"));
        assertEquals("flock.follower_one", beacon.fields().get("consumerFixtureId"));
        assertEquals("consumed", beacon.fields().get("lifecycle"));
        assertEquals(12.0, beacon.fields().get("radius"));
        assertEquals(List.of(), beacon.fields().get("unsupportedFields"));
    }

    @Test
    void emitsMessageAndBeaconTransitionsWhenObservableSignalsAppear() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc previous = observer.observe(null, signalSnapshot(false, false));
        NpcRuntimeObservedNpc current = observer.observe(null, signalSnapshot(true, true));

        List<NpcRuntimeTraceRecord> records = observer.traceRecords(
                "request-a",
                90,
                current,
                previous,
                new NpcRuntimeRequest.EngineHooksSpec(false, false, false, false),
                "npcUnderTest",
                new NpcRuntimeActionObserver.ActionLifecycleTracker(),
                signalFixtures()
        );

        NpcRuntimeTraceRecord messageTransition = records.stream()
                .filter(record -> "message-transition".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
        assertEquals("appeared", messageTransition.fields().get("lifecycle"));
        assertEquals("message.threat", messageTransition.fields().get("fixtureId"));
        assertTrue((Integer) messageTransition.fields().get("changeCount") > 0);

        NpcRuntimeTraceRecord beaconTransition = records.stream()
                .filter(record -> "beacon-transition".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
        assertEquals("appeared", beaconTransition.fields().get("lifecycle"));
        assertEquals("beacon.threat", beaconTransition.fields().get("fixtureId"));
        assertTrue((Integer) beaconTransition.fields().get("changeCount") > 0);
    }

    @Test
    void doesNotInferMessageOrBeaconEvidenceWithoutObservableSnapshotSignals() {
        NpcRuntimeObserver observer = new NpcRuntimeObserver();
        NpcRuntimeObservedNpc observed = observer.observe(null, signalSnapshot(false, false));

        List<NpcRuntimeTraceRecord> records = observer.traceRecords(
                "request-a",
                72,
                observed,
                null,
                new NpcRuntimeRequest.EngineHooksSpec(false, false, false, false),
                "npcUnderTest",
                new NpcRuntimeActionObserver.ActionLifecycleTracker(),
                signalFixtures()
        );

        assertTrue(records.stream().noneMatch(record -> "message-evidence".equals(record.fields().get("kind"))));
        assertTrue(records.stream().noneMatch(record -> "beacon-evidence".equals(record.fields().get("kind"))));
        NpcRuntimeTraceRecord surface = records.stream()
                .filter(record -> "signal-surface".equals(record.fields().get("kind")))
                .findFirst()
                .orElseThrow();
        assertTrue(surface.fields().get("unsupportedFields").toString().contains("enginePrivateBeaconBus"));
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

    private static NpcDebugSnapshot signalSnapshot(boolean includeMessage, boolean includeBeacon) {
        String messageLines = includeMessage
                ? """
                        - Threat Broadcast: message threat.broadcast receiver=flock.follower_one target=target.Enemy
                        - 00: Message threat.broadcast sent by npcUnderTest to flock.follower_one target target.Enemy
                        """
                : "";
        String beaconLines = includeBeacon
                ? """
                        - Beacon Threat: beacon threat source=npcUnderTest target=target.Enemy consumer=flock.follower_one radius=12 consumed
                        - 01: Beacon threat consumed by flock.follower_one
                        """
                : "";
        return new NpcDebugSnapshot(
                "NPC Debug Inspector",
                "UUID: sample | Loaded: true",
                """
                        === Overview ===
                        - UUID: sample
                        - Role Id: Mob_Test
                        - State: Alert

                        === AI ===
                        - State: Alert
                        - Sub-State: start
                        - Current Tree Step: AlertLoop
                        - Current Body Step: AlertBody

                        === Targeting / Sensors ===
                        - Target Enemy: Sabretooth (00000000-0000-0000-0000-000000000001)
                        %s%s

                        === Timers / Cooldowns ===
                        - Scope Timer Keys: 1

                        === Flock ===
                        - Membership: leader=npcUnderTest

                        === Recent Events ===
                        Events Log: Pin this to mirror recent events in overlay.
                        Event Count: 2
                        %s%s
                        """.formatted(messageLines, beaconLines, messageLines, beaconLines)
        );
    }

    private static List<NpcRuntimeFixtureSpec> signalFixtures() {
        return List.of(
                fixture(Map.of(
                        "fixtureId", "npcUnderTest",
                        "kind", "npcUnderTest",
                        "roleId", "LeaderRole",
                        "position", List.of(0, 64, 0)
                )),
                fixture(Map.of(
                        "fixtureId", "target.Enemy",
                        "kind", "targetDummy",
                        "roleId", "TargetRole",
                        "position", List.of(3, 64, 4),
                        "targetSlot", "Enemy"
                )),
                fixture(Map.of(
                        "fixtureId", "flock.follower_one",
                        "kind", "flockMember",
                        "roleId", "FollowerRole",
                        "position", List.of(-2, 64, 0),
                        "leaderFixtureId", "npcUnderTest"
                )),
                fixture(Map.of(
                        "fixtureId", "message.threat",
                        "kind", "message",
                        "messageId", "threat-1",
                        "messageType", "threat.broadcast",
                        "senderFixtureId", "npcUnderTest",
                        "receiverFixtureId", "flock.follower_one",
                        "targetFixtureId", "target.Enemy",
                        "targetSlot", "Enemy",
                        "payloadKeys", List.of("target", "urgency")
                )),
                fixture(Map.of(
                        "fixtureId", "beacon.threat",
                        "kind", "beacon",
                        "beaconId", "threat-1",
                        "beaconType", "threat",
                        "sourceFixtureId", "npcUnderTest",
                        "targetFixtureId", "target.Enemy",
                        "radius", 12,
                        "ttlTicks", 90,
                        "requiredConsumerFixtureIds", List.of("flock.follower_one")
                ))
        );
    }

    private static NpcRuntimeFixtureSpec fixture(Map<String, Object> fields) {
        return NpcRuntimeFixtureSpec.fromMap(fields, "observer_fixture", "fixtures.list[]", "FallbackRole");
    }
}
