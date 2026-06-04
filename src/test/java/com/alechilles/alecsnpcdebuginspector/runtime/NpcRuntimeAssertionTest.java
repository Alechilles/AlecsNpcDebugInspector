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
