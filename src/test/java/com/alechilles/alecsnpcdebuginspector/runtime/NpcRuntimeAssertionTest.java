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
                "sensorType", "TargetSlot",
                "sensorId", "targetEnemy",
                "matchResult", "matched",
                "unsupportedFields", List.of("distance")
        )));

        assertEquals("failed", result.status());
        assertTrue(result.message().contains("distance band is unsupported"));
    }
}
