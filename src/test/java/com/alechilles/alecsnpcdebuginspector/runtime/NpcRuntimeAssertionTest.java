package com.alechilles.alecsnpcdebuginspector.runtime;

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
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep",
                "expectedLifecycle", "selected",
                "expectedSelected", true
        )))).getFirst();

        NpcRuntimeAssertionResult result = assertion.evaluate(List.of(Map.of(
                "kind", "action-evidence",
                "actionType", "InstructionStep",
                "actionId", "currentTreeStep",
                "lifecycle", "selected",
                "selected", true,
                "unsupportedFields", List.of("preconditions")
        )));

        assertEquals("passed", result.status());
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
}
