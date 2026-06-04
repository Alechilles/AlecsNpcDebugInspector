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
        @Nullable String actionType,
        @Nullable String actionId,
        @Nullable String evaluatorType,
        @Nullable String evaluatorId,
        @Nullable String tameworkSection,
        @Nullable String tameworkField,
        @Nullable String expectedMatchResult,
        @Nullable String expectedLifecycle,
        @Nullable String expectedValue,
        @Nullable String expectedTargetFixtureId,
        @Nullable Boolean expectedPresent,
        @Nullable Boolean expectedSelected,
        @Nullable Boolean expectedEligible,
        @Nullable String expectedAbility,
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
                string(fields, "actionType", "type"),
                string(fields, "actionId", "id"),
                string(fields, "evaluatorType", "type"),
                string(fields, "evaluatorId", "id"),
                string(fields, "section", "tameworkSection"),
                string(fields, "field", "tameworkField"),
                string(fields, "expectedMatchResult", "expectedResult"),
                string(fields, "expectedLifecycle", "lifecycle"),
                string(fields, "expectedValue", "value"),
                string(fields, "expectedTargetFixtureId", "targetFixtureId"),
                boolOrNull(fields.containsKey("expectedPresent") ? fields.get("expectedPresent") : fields.get("present")),
                boolOrNull(fields.containsKey("expectedSelected") ? fields.get("expectedSelected") : fields.get("selected")),
                boolOrNull(fields.containsKey("expectedEligible") ? fields.get("expectedEligible") : fields.get("eligible")),
                string(fields, "expectedAbility", "ability"),
                listOrNull(fields.get("expectedDistanceBand")),
                boolOrNull(fields.get("expectUnsupported"))
        );
    }

    @Nonnull
    NpcRuntimeAssertionResult evaluate(@Nonnull List<Map<String, Object>> evidenceRecords) {
        if (!supportedKind()) {
            return NpcRuntimeAssertionResult.unknown(assertionId, kind, "unsupported assertion kind: " + kind);
        }
        Map<String, Object> evidence = matchingEvidence(evidenceRecords);
        if (evidence == null) {
            return NpcRuntimeAssertionResult.unknown(assertionId, kind, "no matching " + kind + " evidence was observed");
        }
        ArrayList<String> failures = new ArrayList<>();
        if (expectedMatchResult != null && !matchesText(expectedMatchResult, evidence.get("matchResult"))) {
            failures.add("expected matchResult=" + expectedMatchResult + " but observed " + evidence.get("matchResult"));
        }
        if (expectedLifecycle != null && !matchesText(expectedLifecycle, evidence.get("lifecycle"))) {
            failures.add("expected lifecycle=" + expectedLifecycle + " but observed " + evidence.get("lifecycle"));
        }
        if (expectedValue != null && !matchesText(expectedValue, evidence.get("observedValue"))) {
            failures.add("expected value=" + expectedValue + " but observed " + evidence.get("observedValue"));
        }
        if (expectedPresent != null && !matchesBoolean(expectedPresent, evidence.get("present"))) {
            failures.add("expected present=" + expectedPresent + " but observed " + evidence.get("present"));
        }
        if (expectedSelected != null && !matchesBoolean(expectedSelected, evidence.get("selected"))) {
            failures.add("expected selected=" + expectedSelected + " but observed " + evidence.get("selected"));
        }
        if (expectedEligible != null) {
            Object unsupported = evidence.get("unsupportedFields");
            if (containsValue(unsupported, "eligibility") || !evidence.containsKey("eligible") || evidence.get("eligible") == null) {
                failures.add("eligibility is unsupported by this evidence");
            } else if (!matchesBoolean(expectedEligible, evidence.get("eligible"))) {
                failures.add("expected eligible=" + expectedEligible + " but observed " + evidence.get("eligible"));
            }
        }
        if (expectedAbility != null) {
            if (containsValue(evidence.get("unsupportedFields"), "chosenAction") || !evidence.containsKey("ability")) {
                failures.add("ability selection is unsupported by this evidence");
            } else if (!matchesText(expectedAbility, evidence.get("ability"))) {
                failures.add("expected ability=" + expectedAbility + " but observed " + evidence.get("ability"));
            }
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
    private Map<String, Object> matchingEvidence(@Nonnull List<Map<String, Object>> evidenceRecords) {
        String evidenceKind = evidenceKind();
        for (Map<String, Object> evidence : evidenceRecords) {
            if (!matchesText(evidenceKind, evidence.get("kind"))) {
                continue;
            }
            if (sensorType != null && !matchesText(sensorType, evidence.get("sensorType"))) {
                continue;
            }
            if (sensorId != null && !matchesText(sensorId, evidence.get("sensorId"))) {
                continue;
            }
            if (actionType != null && !matchesText(actionType, evidence.get("actionType"))) {
                continue;
            }
            if (actionId != null && !matchesText(actionId, evidence.get("actionId"))) {
                continue;
            }
            if (evaluatorType != null && !matchesText(evaluatorType, evidence.get("evaluatorType"))) {
                continue;
            }
            if (evaluatorId != null && !matchesText(evaluatorId, evidence.get("evaluatorId"))) {
                continue;
            }
            if (tameworkSection != null && !matchesText(tameworkSection, evidence.get("section"))) {
                continue;
            }
            if (tameworkField != null && !matchesText(tameworkField, evidence.get("field"))) {
                continue;
            }
            return evidence;
        }
        return null;
    }

    private boolean supportedKind() {
        return "sensor".equalsIgnoreCase(kind)
                || "action".equalsIgnoreCase(kind)
                || "combat".equalsIgnoreCase(kind)
                || "combat-evaluator".equalsIgnoreCase(kind)
                || "tamework".equalsIgnoreCase(kind);
    }

    @Nonnull
    private String evidenceKind() {
        if ("sensor".equalsIgnoreCase(kind)) {
            return "sensor-evidence";
        }
        if ("action".equalsIgnoreCase(kind)) {
            return "action-evidence";
        }
        if ("tamework".equalsIgnoreCase(kind)) {
            return "tamework-evidence";
        }
        return "combat-evaluator-evidence";
    }

    private static boolean matchesText(@Nonnull String expected, @Nullable Object observed) {
        return observed instanceof String text && expected.toLowerCase(Locale.ROOT).equals(text.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesBoolean(boolean expected, @Nullable Object observed) {
        return observed instanceof Boolean bool && expected == bool;
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
