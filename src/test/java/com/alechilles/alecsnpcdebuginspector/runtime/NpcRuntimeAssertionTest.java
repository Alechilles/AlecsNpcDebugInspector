package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRuntimeAssertionTest {
    @Test
    void passesWhenExpectedSensorEvidenceMatches() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "sensor",
                "assertionId", "target-present",
                "sensorType", "TargetSlot",
                "sensorId", "targetEnemy",
                "expectedMatchResult", "matched",
                "expectUnsupported", false
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "sensor-evidence",
                "sensorType", "TargetSlot",
                "sensorId", "targetEnemy",
                "matchResult", "matched",
                "observedValue", "Enemy",
                "unsupportedFields", List.of()
        )));

        assertEquals("passed", result.status());
        assertEquals("target-present", result.assertionId());
    }

    @Test
    void failsWhenExpectedSensorEvidenceDoesNotMatch() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "sensor",
                "sensorType", "TargetSlot",
                "sensorId", "targetEnemy",
                "expectedMatchResult", "matched"
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "sensor-evidence",
                "sensorType", "TargetSlot",
                "sensorId", "targetEnemy",
                "matchResult", "not-matched",
                "unsupportedFields", List.of()
        )));

        assertEquals("failed", result.status());
        assertTrue(result.message().contains("expected matchResult=matched"));
    }

    @Test
    void reportsUnknownWhenNoMatchingSensorEvidenceExists() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "sensor",
                "sensorType", "TargetSlot",
                "sensorId", "targetEnemy",
                "expectedMatchResult", "matched"
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of());

        assertEquals("unknown", result.status());
        assertTrue(result.message().contains("no matching sensor evidence"));
    }

    @Test
    void failsDistanceBandExpectationWhenDistanceEvidenceIsUnsupported() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "sensor",
                "sensorType", "TargetSlot",
                "sensorId", "targetEnemy",
                "expectedDistanceBand", List.of(0, 4)
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "sensor-evidence",
                "sensorType", "TargetSlot",
                "sensorId", "targetEnemy",
                "matchResult", "matched",
                "unsupportedFields", List.of("distance")
        )));

        assertEquals("failed", result.status());
        assertTrue(result.message().contains("distance band is unsupported"));
    }

    @Test
    void passesWhenExpectedActionEvidenceMatches() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "action",
                "fixtureId", "npcUnderTest",
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep",
                "expectedLifecycle", "selected",
                "expectedSelected", true
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "action-evidence",
                "fixtureId", "npcUnderTest",
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep",
                "lifecycle", "selected",
                "selected", true,
                "unsupportedFields", List.of("preconditions")
        )));

        assertEquals("passed", result.status());
    }

    @Test
    void passesWhenExpectedActionStartTransitionMatches() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "action",
                "assertionId", "attack-started",
                "expectedEventKind", "action-start",
                "fixtureId", "npcUnderTest",
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep",
                "expectedLifecycle", "selected"
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "action-start",
                "fixtureId", "npcUnderTest",
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep",
                "lifecycle", "selected",
                "currentValue", "Sequence[Attack]",
                "startTick", 42
        )));

        assertEquals("passed", result.status());
        assertEquals(42, result.firstMatchedTick());
    }

    @Test
    void passesWhenExpectedActionEndTransitionMatches() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "action",
                "assertionId", "attack-ended",
                "expectedEventKind", "action-end",
                "fixtureId", "npcUnderTest",
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep"
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "action-end",
                "fixtureId", "npcUnderTest",
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep",
                "lifecycle", "selected",
                "previousValue", "Sequence[Attack]",
                "endTick", 55
        )));

        assertEquals("passed", result.status());
        assertEquals(55, result.firstMatchedTick());
    }

    @Test
    void ignoresEvidenceFromDifferentFixtureId() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "action",
                "fixtureId", "npcUnderTest",
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep",
                "expectedLifecycle", "selected"
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "action-evidence",
                "fixtureId", "flock.follower_one",
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep",
                "lifecycle", "selected",
                "unsupportedFields", List.of()
        )));

        assertEquals("unknown", result.status());
        assertTrue(result.message().contains("no matching action evidence"));
    }

    @Test
    void failsWhenCombatEligibilityIsUnsupported() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "combat-evaluator",
                "evaluatorType", "CombatSupport",
                "evaluatorId", "combatSupport.executingAttack",
                "expectedEligible", true
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "combat-evaluator-evidence",
                "evaluatorType", "CombatSupport",
                "evaluatorId", "combatSupport.executingAttack",
                "selected", false,
                "unsupportedFields", List.of("eligibility")
        )));

        assertEquals("failed", result.status());
        assertTrue(result.message().contains("eligibility is unsupported"));
    }

    @Test
    void passesWhenExpectedTameworkEvidenceMatches() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "tamework",
                "section", "tamework",
                "field", "pluginLoaded",
                "expectedValue", "true",
                "expectedPresent", true,
                "expectUnsupported", true
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "tamework-evidence",
                "section", "tamework",
                "field", "pluginLoaded",
                "present", true,
                "observedValue", "true",
                "unsupportedFields", List.of("fixtureMutation")
        )));

        assertEquals("passed", result.status());
    }

    @Test
    void failsWhenExpectedTameworkValueDiffers() {
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(new NpcRuntimeRequest.AssertionSpec(Map.of(
                "kind", "tamework",
                "section", "tameworkDiagnostics",
                "field", "healthStatus",
                "expectedValue", "HEALTHY"
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "tamework-evidence",
                "section", "tameworkDiagnostics",
                "field", "healthStatus",
                "present", true,
                "observedValue", "DEGRADED",
                "unsupportedFields", List.of()
        )));

        assertEquals("failed", result.status());
        assertTrue(result.message().contains("expected value=HEALTHY"));
    }

    @Test
    void eventuallyWindowIgnoresWarmupEvidenceAndPassesOnLaterMatch() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "target-eventual",
                "kind", "sensor",
                "sensorType", "TargetSlot",
                "sensorId", "targetLockedtarget",
                "expectedMatchResult", "matched",
                "window", Map.of("mode", "eventually", "startTick", 60, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(10, "sensor-evidence", "sensorType", "TargetSlot", "sensorId", "targetLockedtarget", "matchResult", "matched"),
                evidence(80, "sensor-evidence", "sensorType", "TargetSlot", "sensorId", "targetLockedtarget", "matchResult", "matched")
        ));

        assertEquals("passed", result.status());
        assertEquals(80, result.firstMatchedTick());
    }

    @Test
    void sustainedWindowRequiresConsecutiveMatchingTicks() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "combat-sustained",
                "kind", "combat-evaluator",
                "evaluatorId", "combatSupport.executingAttack",
                "expectedLifecycle", "running",
                "window", Map.of("mode", "sustained", "startTick", 20, "endTick", 80, "sustainedTicks", 3)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(40, "combat-evaluator-evidence", "evaluatorId", "combatSupport.executingAttack", "lifecycle", "running"),
                evidence(41, "combat-evaluator-evidence", "evaluatorId", "combatSupport.executingAttack", "lifecycle", "running"),
                evidence(42, "combat-evaluator-evidence", "evaluatorId", "combatSupport.executingAttack", "lifecycle", "running")
        ));

        assertEquals("passed", result.status());
        assertEquals(40, result.firstMatchedTick());
        assertEquals(42, result.lastMatchedTick());
    }

    @Test
    void neverWindowFailsWhenMatchingEvidenceAppears() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "no-attack",
                "kind", "combat-evaluator",
                "evaluatorId", "combatSupport.executingAttack",
                "expectedLifecycle", "running",
                "window", Map.of("mode", "never", "startTick", 0, "endTick", 60)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(30, "combat-evaluator-evidence", "evaluatorId", "combatSupport.executingAttack", "lifecycle", "running")
        ));

        assertEquals("failed", result.status());
        assertTrue(result.message().contains("forbidden evidence observed"));
    }

    @Test
    void passesFlockAssertionFromFixtureLinks() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "flock-links",
                "kind", "flock",
                "fixtureId", "npcUnderTest",
                "expectedLeaderFixtureId", "npcUnderTest",
                "expectedMemberCount", 3,
                "expectUnsupported", true,
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(0, "fixture-link", "fixture", "flock.follower_one", "relationship", "flockLeader", "targetFixtureId", "npcUnderTest", "unsupportedFields", List.of("engineFlockMembershipMutation")),
                evidence(0, "fixture-link", "fixture", "flock.follower_two", "relationship", "flockLeader", "targetFixtureId", "npcUnderTest", "unsupportedFields", List.of("engineFlockMembershipMutation"))
        ));

        assertEquals("passed", result.status());
        assertEquals(0, result.firstMatchedTick());
        assertEquals(2, result.matchedEvidenceCount());
        assertEquals(3, result.evidence().get("observedMemberCount"));
    }

    @Test
    void passesFlockAssertionFromFlockEvidence() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "flock-evidence",
                "kind", "flock",
                "fixtureId", "flock.follower_one",
                "expectedLeaderFixtureId", "npcUnderTest",
                "expectedMemberCount", 3,
                "expectedDistanceBand", List.of(0, 6),
                "expectUnsupported", true,
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(0, "flock-evidence",
                        "fixtureId", "flock.follower_one",
                        "leaderFixtureId", "npcUnderTest",
                        "memberCount", 3,
                        "distanceToLeader", 4.5,
                        "unsupportedFields", List.of("engineFlockMembershipMutation"))
        ));

        assertEquals("passed", result.status());
        assertEquals(0, result.firstMatchedTick());
        assertEquals(1, result.matchedEvidenceCount());
        assertEquals(3, result.evidence().get("observedMemberCount"));
        assertEquals(4.5, result.evidence().get("observedDistance"));
    }

    @Test
    void passesFamilyAssertionFromFlockEvidenceParentField() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "family-evidence",
                "kind", "flock",
                "fixtureId", "family.child",
                "expectedParentFixtureId", "npcUnderTest",
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(0, "flock-evidence",
                        "fixtureId", "family.child",
                        "parentFixtureId", "npcUnderTest",
                        "unsupportedFields", List.of("engineFamilyBindingMutation"))
        ));

        assertEquals("passed", result.status());
        assertEquals(0, result.firstMatchedTick());
    }

    @Test
    void passesMessageAssertionFromMessageEvidence() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "message-received",
                "kind", "message",
                "expectedMessageType", "threat.broadcast",
                "expectedSenderFixtureId", "npcUnderTest",
                "expectedReceiverFixtureId", "flock.follower_one",
                "expectedTargetFixtureId", "target.Enemy",
                "expectUnsupported", true,
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(18, "message-evidence",
                        "messageType", "threat.broadcast",
                        "senderFixtureId", "npcUnderTest",
                        "receiverFixtureId", "flock.follower_one",
                        "targetFixtureId", "target.Enemy",
                        "unsupportedFields", List.of("engineMessageBusMutation"))
        ));

        assertEquals("passed", result.status());
        assertEquals(18, result.firstMatchedTick());
    }

    @Test
    void passesMessageAssertionForAllExpectedReceivers() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "broadcast-message",
                "kind", "message",
                "expectedMessageType", "threat.broadcast",
                "expectedReceiverCount", 2,
                "allFixtures", List.of("flock.follower_one", "flock.follower_two"),
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(18, "message-evidence",
                        "messageType", "threat.broadcast",
                        "receiverFixtureId", "flock.follower_one",
                        "unsupportedFields", List.of()),
                evidence(25, "message-evidence",
                        "messageType", "threat.broadcast",
                        "receiverFixtureId", "flock.follower_two",
                        "unsupportedFields", List.of())
        ));

        assertEquals("passed", result.status());
        assertEquals(2, result.evidence().get("observedReceiverCount"));
        assertEquals(List.of("flock.follower_one", "flock.follower_two"), result.evidence().get("allFixtures"));
    }

    @Test
    void failsMessageAssertionWhenAllFixtureSelectorMissesReceiver() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "broadcast-message",
                "kind", "message",
                "expectedMessageType", "threat.broadcast",
                "allFixtures", List.of("flock.follower_one", "flock.follower_two"),
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(18, "message-evidence",
                        "messageType", "threat.broadcast",
                        "receiverFixtureId", "flock.follower_one",
                        "unsupportedFields", List.of())
        ));

        assertEquals("failed", result.status());
        assertTrue(result.message().contains("missing required fixture evidence"));
    }

    @Test
    void passesMessageAssertionWhenAnyFixtureSelectorMatchesReceiver() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "any-message",
                "kind", "message",
                "expectedMessageType", "threat.broadcast",
                "anyFixture", List.of("flock.follower_one", "flock.follower_two"),
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(18, "message-evidence",
                        "messageType", "threat.broadcast",
                        "receiverFixtureId", "flock.follower_two",
                        "unsupportedFields", List.of())
        ));

        assertEquals("passed", result.status());
        assertEquals(List.of("flock.follower_one", "flock.follower_two"), result.evidence().get("anyFixture"));
    }

    @Test
    void returnsUnknownForMissingMessageEvidence() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "message-missing",
                "kind", "message",
                "expectedMessageType", "threat.broadcast",
                "expectedReceiverFixtureId", "flock.follower_one",
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of());

        assertEquals("unknown", result.status());
        assertTrue(result.message().contains("no matching message evidence"));
    }

    @Test
    void neverMessageAssertionFailsWhenForbiddenMessageAppears() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "no-message",
                "kind", "message",
                "expectedMessageType", "threat.broadcast",
                "window", Map.of("mode", "never", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(18, "message-evidence",
                        "messageType", "threat.broadcast",
                        "receiverFixtureId", "flock.follower_one",
                        "unsupportedFields", List.of())
        ));

        assertEquals("failed", result.status());
        assertTrue(result.message().contains("forbidden evidence observed"));
    }

    @Test
    void withinDeliveryWindowTicksAcceptsOnTimeAndRejectsLateMessageEvidence() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "message-window",
                "kind", "message",
                "expectedMessageType", "threat.broadcast",
                "withinDeliveryWindowTicks", 90,
                "window", Map.of("mode", "eventually", "startTick", 30, "endTick", 240)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult onTime = assertion.evaluate(List.of(
                evidence(120, "message-evidence", "messageType", "threat.broadcast", "unsupportedFields", List.of())
        ));
        NpcRuntimeAssertionResult late = assertion.evaluate(List.of(
                evidence(121, "message-evidence", "messageType", "threat.broadcast", "unsupportedFields", List.of())
        ));

        assertEquals("passed", onTime.status());
        assertEquals("unknown", late.status());
    }

    @Test
    void passesBeaconAssertionFromBeaconEvidence() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "beacon-consumed",
                "kind", "beacon",
                "expectedBeaconType", "threat",
                "expectedSourceFixtureId", "npcUnderTest",
                "expectedTargetFixtureId", "target.Enemy",
                "expectedConsumerCount", 1,
                "expectUnsupported", true,
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(20, "beacon-evidence",
                        "beaconType", "threat",
                        "sourceFixtureId", "npcUnderTest",
                        "targetFixtureId", "target.Enemy",
                        "consumerFixtureId", "flock.follower_one",
                        "unsupportedFields", List.of("engineBeaconMutation"))
        ));

        assertEquals("passed", result.status());
        assertEquals(20, result.firstMatchedTick());
        assertEquals(1, result.evidence().get("observedConsumerCount"));
    }

    @Test
    void passesBeaconAssertionForAllExpectedConsumers() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "beacon-fanout",
                "kind", "beacon",
                "expectedBeaconType", "threat",
                "expectedConsumerCount", 2,
                "allFixtures", List.of("flock.follower_one", "flock.follower_two"),
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(20, "beacon-evidence",
                        "beaconType", "threat",
                        "consumerFixtureId", "flock.follower_one",
                        "unsupportedFields", List.of()),
                evidence(30, "beacon-evidence",
                        "beaconType", "threat",
                        "consumerFixtureId", "flock.follower_two",
                        "unsupportedFields", List.of())
        ));

        assertEquals("passed", result.status());
        assertEquals(2, result.evidence().get("observedConsumerCount"));
    }

    @Test
    void passesCausalChainAssertionForOrderedSensorAndActionLinks() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "leader-follower-chain",
                "kind", "causal-chain",
                "links", List.of(
                        Map.of(
                                "kind", "sensor",
                                "sourceFixtureId", "npcUnderTest",
                                "targetFixtureId", "target.Enemy",
                                "withinTicks", 90,
                                "classification", "leader-target-missing"
                        ),
                        Map.of(
                                "kind", "action",
                                "evidenceKind", "action-start",
                                "sourceFixtureId", "flock.follower_one",
                                "targetFixtureId", "target.Enemy",
                                "withinTicks", 180,
                                "classification", "follower-reaction-missing"
                        )
                )
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(30, "sensor-evidence",
                        "fixtureId", "npcUnderTest",
                        "targetFixtureId", "target.Enemy",
                        "unsupportedFields", List.of()),
                evidence(120, "action-start",
                        "fixtureId", "flock.follower_one",
                        "targetFixtureId", "target.Enemy",
                        "unsupportedFields", List.of())
        ));

        assertEquals("passed", result.status());
        assertEquals("causal-chain", result.evidence().get("kind"));
        List<?> links = (List<?>) result.evidence().get("links");
        assertEquals("passed", ((Map<?, ?>) links.get(0)).get("status"));
        assertEquals("passed", ((Map<?, ?>) links.get(1)).get("status"));
    }

    @Test
    void lateCausalChainAssertionUsesSemanticClassificationAndTimingDiagnostics() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "late-message-chain",
                "kind", "causal-chain",
                "links", List.of(
                        Map.of(
                                "kind", "message",
                                "sourceFixtureId", "npcUnderTest",
                                "targetFixtureId", "flock.follower_one",
                                "withinTicks", 90,
                                "classification", "message-not-received"
                        )
                )
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(180, "message-evidence",
                        "senderFixtureId", "npcUnderTest",
                        "receiverFixtureId", "flock.follower_one",
                        "unsupportedFields", List.of())
        ));

        assertEquals("failed", result.status());
        Map<?, ?> firstBroken = (Map<?, ?>) result.evidence().get("firstBrokenLink");
        assertEquals("late", firstBroken.get("status"));
        assertEquals("message-receive-missing", firstBroken.get("classification"));
        assertEquals(180, firstBroken.get("observedLatencyTicks"));
        assertEquals(90, firstBroken.get("lateByTicks"));
    }
    @Test
    void failsCausalChainAssertionWithFirstBrokenFanoutLink() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "broadcast-chain",
                "kind", "causal-chain",
                "links", List.of(
                        Map.of(
                                "kind", "message",
                                "sourceFixtureId", "npcUnderTest",
                                "expectedReceiverCount", 3,
                                "withinTicks", 150,
                                "classification", "message-receive-missing"
                        )
                )
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(42, "message-evidence",
                        "senderFixtureId", "npcUnderTest",
                        "receiverFixtureId", "flock.follower_one",
                        "unsupportedFields", List.of()),
                evidence(45, "message-evidence",
                        "senderFixtureId", "npcUnderTest",
                        "receiverFixtureId", "flock.follower_two",
                        "unsupportedFields", List.of())
        ));

        assertEquals("failed", result.status());
        Map<?, ?> firstBroken = (Map<?, ?>) result.evidence().get("firstBrokenLink");
        assertEquals("partial-broadcast-delivery", firstBroken.get("classification"));
        assertEquals("missing", firstBroken.get("status"));
        assertEquals(2, firstBroken.get("observedReceiverCount"));
    }

    @Test
    void failsFamilyAssertionWhenParentLinkDiffers() {
        NpcRuntimeRequest.AssertionSpec spec = assertionSpec(Map.of(
                "assertionId", "family-link",
                "kind", "flock",
                "fixtureId", "family.child",
                "expectedParentFixtureId", "npcUnderTest",
                "window", Map.of("mode", "eventually", "startTick", 0, "endTick", 120)
        ));
        NpcRuntimeAssertion assertion = NpcRuntimeAssertion.fromSpecs(List.of(spec)).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(
                evidence(0, "fixture-link", "fixture", "family.child", "relationship", "parent", "targetFixtureId", "family.other_parent", "unsupportedFields", List.of("engineFamilyBindingMutation"))
        ));

        assertEquals("failed", result.status());
        assertTrue(result.message().contains("expected parentFixtureId=npcUnderTest"));
    }

    private static NpcRuntimeRequest.AssertionSpec assertionSpec(Map<String, Object> fields) {
        Map<String, Object> copy = new LinkedHashMap<>(fields);
        Object rawWindow = copy.get("window");
        NpcRuntimeRequest.AssertionWindowSpec window = rawWindow instanceof Map<?, ?> windowFields
                ? NpcRuntimeRequest.AssertionWindowSpec.from(castMap(windowFields), 0, 120, "test")
                : new NpcRuntimeRequest.AssertionWindowSpec("eventually", 0, 120, 1);
        return new NpcRuntimeRequest.AssertionSpec(copy, window);
    }

    private static Map<String, Object> evidence(int tick, String kind, Object... fields) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("tick", tick);
        map.put("kind", kind);
        for (int i = 0; i < fields.length; i += 2) {
            map.put((String) fields[i], fields[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> castMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return map;
    }
}
