package com.alechilles.alecsnpcdebuginspector.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
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
        @Nullable Boolean expectUnsupported,
        @Nonnull NpcRuntimeRequest.AssertionWindowSpec window
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
                boolOrNull(fields.get("expectUnsupported")),
                spec.window()
        );
    }

    @Nonnull
    NpcRuntimeAssertionResult evaluate(@Nonnull List<Map<String, Object>> evidenceRecords) {
        if (!supportedKind()) {
            return NpcRuntimeAssertionResult.unknown(assertionId, kind, "unsupported assertion kind: " + kind);
        }
        List<Map<String, Object>> candidates = matchingEvidenceCandidates(evidenceRecords);
        if (candidates.isEmpty()) {
            return NpcRuntimeAssertionResult.unknown(assertionId, kind, "no matching " + kind + " evidence was observed");
        }
        if ("never".equalsIgnoreCase(window.mode())) {
            for (Map<String, Object> candidate : candidates) {
                Evaluation evaluation = evaluationFor(candidate);
                if (evaluation.passed()) {
                    Map<String, Object> evidenceCopy = new LinkedHashMap<>(candidate);
                    Integer tick = tickOrNull(candidate);
                    return NpcRuntimeAssertionResult.failed(
                            assertionId,
                            kind,
                            "forbidden evidence observed",
                            evidenceCopy,
                            tick,
                            tick,
                            tick != null ? 1 : 0
                    );
                }
            }
            return NpcRuntimeAssertionResult.passed(assertionId, kind, "forbidden evidence was not observed", null);
        }
        if ("sustained".equalsIgnoreCase(window.mode())) {
            return evaluateSustained(candidates);
        }

        Evaluation firstFailure = null;
        Map<String, Object> firstFailureEvidence = null;
        for (Map<String, Object> candidate : candidates) {
            Evaluation evaluation = evaluationFor(candidate);
            if (evaluation.passed()) {
                Map<String, Object> evidenceCopy = new LinkedHashMap<>(candidate);
                Integer tick = tickOrNull(candidate);
                return NpcRuntimeAssertionResult.passed(
                        assertionId,
                        kind,
                        "assertion passed",
                        evidenceCopy,
                        tick,
                        tick,
                        tick != null ? 1 : 0
                );
            }
            if (firstFailure == null) {
                firstFailure = evaluation;
                firstFailureEvidence = candidate;
            }
        }
        if (firstFailure != null && firstFailureEvidence != null) {
            Map<String, Object> evidenceCopy = new LinkedHashMap<>(firstFailureEvidence);
            Integer tick = tickOrNull(firstFailureEvidence);
            return NpcRuntimeAssertionResult.failed(
                    assertionId,
                    kind,
                    String.join("; ", firstFailure.failures()),
                    evidenceCopy,
                    tick,
                    tick,
                    tick != null ? 1 : 0
            );
        }
        return NpcRuntimeAssertionResult.unknown(assertionId, kind, "no matching " + kind + " evidence was observed");
    }

    boolean canResolveBeforeEnd() {
        return !"never".equalsIgnoreCase(window.mode());
    }

    @Nonnull
    private NpcRuntimeAssertionResult evaluateSustained(@Nonnull List<Map<String, Object>> candidates) {
        TreeSet<Integer> passingTicks = new TreeSet<>();
        LinkedHashMap<Integer, Map<String, Object>> evidenceByTick = new LinkedHashMap<>();
        Evaluation firstFailure = null;
        Map<String, Object> firstFailureEvidence = null;
        for (Map<String, Object> candidate : candidates) {
            Evaluation evaluation = evaluationFor(candidate);
            if (evaluation.passed()) {
                Integer tick = tickOrNull(candidate);
                if (tick == null && window.sustainedTicks() <= 1) {
                    return NpcRuntimeAssertionResult.passed(assertionId, kind, "assertion passed", new LinkedHashMap<>(candidate));
                }
                if (tick != null) {
                    passingTicks.add(tick);
                    evidenceByTick.putIfAbsent(tick, candidate);
                }
            } else if (firstFailure == null) {
                firstFailure = evaluation;
                firstFailureEvidence = candidate;
            }
        }

        int streak = 0;
        int streakStart = -1;
        int previous = Integer.MIN_VALUE;
        for (int tick : passingTicks) {
            if (streak == 0 || tick != previous + 1) {
                streak = 1;
                streakStart = tick;
            } else {
                streak++;
            }
            if (streak >= window.sustainedTicks()) {
                int lastTick = tick;
                Map<String, Object> evidence = new LinkedHashMap<>(evidenceByTick.get(streakStart));
                return NpcRuntimeAssertionResult.passed(
                        assertionId,
                        kind,
                        "assertion passed",
                        evidence,
                        streakStart,
                        lastTick,
                        streak
                );
            }
            previous = tick;
        }

        if (firstFailure != null && firstFailureEvidence != null) {
            Map<String, Object> evidenceCopy = new LinkedHashMap<>(firstFailureEvidence);
            Integer tick = tickOrNull(firstFailureEvidence);
            return NpcRuntimeAssertionResult.failed(
                    assertionId,
                    kind,
                    String.join("; ", firstFailure.failures()),
                    evidenceCopy,
                    tick,
                    tick,
                    tick != null ? 1 : 0
            );
        }
        return NpcRuntimeAssertionResult.failed(
                assertionId,
                kind,
                "expected sustained match for " + window.sustainedTicks() + " ticks but observed " + passingTicks.size(),
                null,
                passingTicks.isEmpty() ? null : passingTicks.getFirst(),
                passingTicks.isEmpty() ? null : passingTicks.getLast(),
                passingTicks.size()
        );
    }

    @Nonnull
    private Evaluation evaluationFor(@Nonnull Map<String, Object> evidence) {
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
        return new Evaluation(failures.isEmpty(), failures);
    }

    @Nonnull
    private List<Map<String, Object>> matchingEvidenceCandidates(@Nonnull List<Map<String, Object>> evidenceRecords) {
        return evidenceRecords.stream()
                .filter(this::matchesIdentity)
                .filter(this::withinWindow)
                .toList();
    }

    private boolean matchesIdentity(@Nonnull Map<String, Object> evidence) {
        if (!matchesText(evidenceKind(), evidence.get("kind"))) {
            return false;
        }
        if (sensorType != null && !matchesText(sensorType, evidence.get("sensorType"))) {
            return false;
        }
        if (sensorId != null && !matchesText(sensorId, evidence.get("sensorId"))) {
            return false;
        }
        if (actionType != null && !matchesText(actionType, evidence.get("actionType"))) {
            return false;
        }
        if (actionId != null && !matchesText(actionId, evidence.get("actionId"))) {
            return false;
        }
        if (evaluatorType != null && !matchesText(evaluatorType, evidence.get("evaluatorType"))) {
            return false;
        }
        if (evaluatorId != null && !matchesText(evaluatorId, evidence.get("evaluatorId"))) {
            return false;
        }
        if (tameworkSection != null && !matchesText(tameworkSection, evidence.get("section"))) {
            return false;
        }
        return tameworkField == null || matchesText(tameworkField, evidence.get("field"));
    }

    private boolean withinWindow(@Nonnull Map<String, Object> evidence) {
        Integer tick = tickOrNull(evidence);
        return tick == null || (tick >= window.startTick() && tick <= window.endTick());
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

    @Nullable
    private static Integer tickOrNull(@Nonnull Map<String, Object> evidence) {
        Object tick = evidence.get("tick");
        return tick instanceof Number number ? number.intValue() : null;
    }

    private record Evaluation(boolean passed, @Nonnull List<String> failures) {
    }
}
