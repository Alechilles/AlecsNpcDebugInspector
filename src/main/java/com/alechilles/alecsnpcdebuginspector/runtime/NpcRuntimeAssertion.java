package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime assertion evaluated against live trace evidence.
 */
public record NpcRuntimeAssertion(
        @Nonnull String assertionId,
        @Nonnull String kind,
        @Nullable String sensorType,
        @Nullable String sensorId,
        @Nullable String expectedMatchResult,
        @Nullable String expectedTargetFixtureId,
        @Nullable List<Object> expectedDistanceBand,
        @Nullable Boolean expectUnsupported
) {
    @Nonnull
    static List<NpcRuntimeAssertion> fromSpecs(@Nonnull List<NpcRuntimeRequest.AssertionSpec> specs) {
        ArrayList<NpcRuntimeAssertion> assertions = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            assertions.add(fromSpec(specs.get(i), i));
        }
        return List.copyOf(assertions);
    }

    @Nonnull
    private static NpcRuntimeAssertion fromSpec(@Nonnull NpcRuntimeRequest.AssertionSpec spec, int index) {
        Map<String, Object> fields = spec.fields();
        String kind = string(fields, "kind", "type");
        if (kind == null || kind.isBlank()) {
            kind = "sensor";
        }
        String assertionId = string(fields, "assertionId", "id");
        if (assertionId == null || assertionId.isBlank()) {
            assertionId = "assertion-" + index;
        }
        return new NpcRuntimeAssertion(
                assertionId,
                kind,
                string(fields, "sensorType", "type"),
                string(fields, "sensorId", "id"),
                string(fields, "expectedMatchResult", "expectedResult"),
                string(fields, "expectedTargetFixtureId", "targetFixtureId"),
                listOrNull(fields.get("expectedDistanceBand")),
                boolOrNull(fields.get("expectUnsupported"))
        );
    }

    @Nonnull
    NpcRuntimeAssertionResult evaluate(@Nonnull List<Map<String, Object>> sensorEvidence) {
        if (!"sensor".equalsIgnoreCase(kind)) {
            return NpcRuntimeAssertionResult.unknown(assertionId, kind, "unsupported assertion kind: " + kind);
        }
        Map<String, Object> evidence = matchingEvidence(sensorEvidence);
        if (evidence == null) {
            return NpcRuntimeAssertionResult.unknown(assertionId, kind, "no matching sensor evidence was observed");
        }
        ArrayList<String> failures = new ArrayList<>();
        if (expectedMatchResult != null && !matchesText(expectedMatchResult, evidence.get("matchResult"))) {
            failures.add("expected matchResult=" + expectedMatchResult + " but observed " + evidence.get("matchResult"));
        }
        if (expectedTargetFixtureId != null) {
            Object unsupported = evidence.get("unsupportedFields");
            if (containsValue(unsupported, "targetFixtureId") || !evidence.containsKey("targetFixtureId")) {
                failures.add("target fixture id is unsupported by this evidence");
            } else if (!matchesText(expectedTargetFixtureId, evidence.get("targetFixtureId"))) {
                failures.add("expected targetFixtureId=" + expectedTargetFixtureId + " but observed " + evidence.get("targetFixtureId"));
            }
        }
        if (expectedDistanceBand != null) {
            Object unsupported = evidence.get("unsupportedFields");
            if (containsValue(unsupported, "distance") || !evidence.containsKey("distance")) {
                failures.add("distance band is unsupported by this evidence");
            }
        }
        if (expectUnsupported != null) {
            boolean observedUnsupported = evidence.get("unsupportedFields") instanceof List<?> list && !list.isEmpty();
            if (expectUnsupported != observedUnsupported) {
                failures.add("expected unsupported=" + expectUnsupported + " but observed " + observedUnsupported);
            }
        }
        Map<String, Object> evidenceCopy = new LinkedHashMap<>(evidence);
        if (failures.isEmpty()) {
            return NpcRuntimeAssertionResult.passed(assertionId, kind, "assertion passed", evidenceCopy);
        }
        return NpcRuntimeAssertionResult.failed(assertionId, kind, String.join("; ", failures), evidenceCopy);
    }

    @Nullable
    private Map<String, Object> matchingEvidence(@Nonnull List<Map<String, Object>> sensorEvidence) {
        for (Map<String, Object> evidence : sensorEvidence) {
            if (sensorType != null && !matchesText(sensorType, evidence.get("sensorType"))) {
                continue;
            }
            if (sensorId != null && !matchesText(sensorId, evidence.get("sensorId"))) {
                continue;
            }
            return evidence;
        }
        return null;
    }

    private static boolean matchesText(@Nonnull String expected, @Nullable Object observed) {
        return observed instanceof String text && expected.toLowerCase(Locale.ROOT).equals(text.toLowerCase(Locale.ROOT));
    }

    @Nullable
    private static String string(@Nonnull Map<String, Object> fields, @Nonnull String first, @Nonnull String second) {
        Object value = fields.containsKey(first) ? fields.get(first) : fields.get(second);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    @Nullable
    private static Boolean boolOrNull(@Nullable Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    @Nullable
    private static List<Object> listOrNull(@Nullable Object value) {
        return value instanceof List<?> list ? List.copyOf(list) : null;
    }

    private static boolean containsValue(@Nullable Object value, @Nonnull String expected) {
        return value instanceof List<?> list && list.stream().anyMatch(item -> matchesText(expected, item));
    }
}
