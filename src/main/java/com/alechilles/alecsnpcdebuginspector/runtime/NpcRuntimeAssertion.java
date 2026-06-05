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
        @Nullable String fixtureId,
        @Nullable String sensorType,
        @Nullable String sensorId,
        @Nullable String actionType,
        @Nullable String actionId,
        @Nullable String evaluatorType,
        @Nullable String evaluatorId,
        @Nullable String tameworkSection,
        @Nullable String tameworkField,
        @Nullable String expectedMatchResult,
        @Nullable String expectedEventKind,
        @Nullable String expectedLifecycle,
        @Nullable String expectedValue,
        @Nullable String expectedTargetFixtureId,
        @Nullable Boolean expectedPresent,
        @Nullable Boolean expectedSelected,
        @Nullable Boolean expectedEligible,
        @Nullable String expectedAbility,
        @Nullable List<Object> expectedDistanceBand,
        @Nullable List<Object> allFixtures,
        @Nullable List<Object> anyFixture,
        @Nullable Integer withinDeliveryWindowTicks,
        @Nullable String expectedLeaderFixtureId,
        @Nullable String expectedParentFixtureId,
        @Nullable Integer expectedMemberCount,
        @Nullable Integer expectedChildCount,
        @Nullable String expectedMessageType,
        @Nullable String expectedSenderFixtureId,
        @Nullable String expectedReceiverFixtureId,
        @Nullable Integer expectedReceiverCount,
        @Nullable String expectedBeaconType,
        @Nullable String expectedSourceFixtureId,
        @Nullable Integer expectedConsumerCount,
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
                string(fields, "fixtureId", "fixture"),
                string(fields, "sensorType", "type"),
                string(fields, "sensorId", "id"),
                string(fields, "actionType", "type"),
                string(fields, "actionId", "id"),
                string(fields, "evaluatorType", "type"),
                string(fields, "evaluatorId", "id"),
                string(fields, "section", "tameworkSection"),
                string(fields, "field", "tameworkField"),
                string(fields, "expectedMatchResult", "expectedResult"),
                string(fields, "expectedEventKind", "eventKind"),
                string(fields, "expectedLifecycle", "lifecycle"),
                string(fields, "expectedValue", "value"),
                string(fields, "expectedTargetFixtureId", "targetFixtureId"),
                boolOrNull(fields.containsKey("expectedPresent") ? fields.get("expectedPresent") : fields.get("present")),
                boolOrNull(fields.containsKey("expectedSelected") ? fields.get("expectedSelected") : fields.get("selected")),
                boolOrNull(fields.containsKey("expectedEligible") ? fields.get("expectedEligible") : fields.get("eligible")),
                string(fields, "expectedAbility", "ability"),
                listOrNull(fields.get("expectedDistanceBand")),
                listOrNull(fields.get("allFixtures")),
                listOrNull(fields.containsKey("anyFixture") ? fields.get("anyFixture") : fields.get("anyFixtures")),
                intOrNull(fields.get("withinDeliveryWindowTicks")),
                string(fields, "expectedLeaderFixtureId", "leaderFixtureId"),
                string(fields, "expectedParentFixtureId", "parentFixtureId"),
                intOrNull(fields.get("expectedMemberCount")),
                intOrNull(fields.get("expectedChildCount")),
                string(fields, "expectedMessageType", "messageType"),
                string(fields, "expectedSenderFixtureId", "senderFixtureId"),
                string(fields, "expectedReceiverFixtureId", "receiverFixtureId"),
                intOrNull(fields.get("expectedReceiverCount")),
                string(fields, "expectedBeaconType", "beaconType"),
                string(fields, "expectedSourceFixtureId", "sourceFixtureId"),
                intOrNull(fields.get("expectedConsumerCount")),
                boolOrNull(fields.get("expectUnsupported")),
                spec.window()
        );
    }

    @Nonnull
    NpcRuntimeAssertionResult evaluate(@Nonnull List<Map<String, Object>> evidenceRecords) {
        if (!supportedKind()) {
            return NpcRuntimeAssertionResult.unknown(assertionId, kind, "unsupported assertion kind: " + kind);
        }
        if (isFlockKind()) {
            return evaluateFlock(evidenceRecords);
        }
        if (isMessageKind()) {
            return evaluateMessage(evidenceRecords);
        }
        if (isBeaconKind()) {
            return evaluateBeacon(evidenceRecords);
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
    private NpcRuntimeAssertionResult evaluateFlock(@Nonnull List<Map<String, Object>> evidenceRecords) {
        List<Map<String, Object>> candidates = evidenceRecords.stream()
                .filter(evidence -> matchesText("fixture-link", evidence.get("kind")) || matchesText("flock-evidence", evidence.get("kind")))
                .filter(this::withinWindow)
                .toList();
        if (candidates.isEmpty()) {
            return NpcRuntimeAssertionResult.unknown(assertionId, kind, "no matching flock evidence was observed");
        }

        FlockEvaluation evaluation = evaluateFlockCandidates(candidates);
        if ("never".equalsIgnoreCase(window.mode())) {
            return evaluation.passed()
                    ? NpcRuntimeAssertionResult.failed(assertionId, kind, "forbidden evidence observed", evaluation.evidence(), evaluation.firstTick(), evaluation.lastTick(), evaluation.matchedCount())
                    : NpcRuntimeAssertionResult.passed(assertionId, kind, "forbidden evidence was not observed", null);
        }
        if (evaluation.passed()) {
            return NpcRuntimeAssertionResult.passed(assertionId, kind, "assertion passed", evaluation.evidence(), evaluation.firstTick(), evaluation.lastTick(), evaluation.matchedCount());
        }
        return NpcRuntimeAssertionResult.failed(assertionId, kind, String.join("; ", evaluation.failures()), evaluation.evidence(), evaluation.firstTick(), evaluation.lastTick(), evaluation.matchedCount());
    }

    @Nonnull
    private FlockEvaluation evaluateFlockCandidates(@Nonnull List<Map<String, Object>> candidates) {
        ArrayList<String> failures = new ArrayList<>();
        List<Map<String, Object>> flockLinks = candidates.stream()
                .filter(evidence -> matchesText("flockLeader", evidence.get("relationship")))
                .toList();
        List<Map<String, Object>> parentLinks = candidates.stream()
                .filter(evidence -> matchesText("parent", evidence.get("relationship")))
                .toList();
        List<Map<String, Object>> flockEvidence = candidates.stream()
                .filter(evidence -> matchesText("flock-evidence", evidence.get("kind")))
                .toList();

        if (expectedLeaderFixtureId != null && !leaderMatches(flockLinks, flockEvidence)) {
            failures.add("expected leaderFixtureId=" + expectedLeaderFixtureId);
        }
        if (expectedParentFixtureId != null && !parentMatches(parentLinks, flockEvidence)) {
            failures.add("expected parentFixtureId=" + expectedParentFixtureId);
        }
        Integer observedMemberCount = null;
        if (expectedMemberCount != null) {
            observedMemberCount = observedMemberCount(flockLinks, flockEvidence);
            if (observedMemberCount == null || observedMemberCount != expectedMemberCount) {
                failures.add("expected memberCount=" + expectedMemberCount + " but observed " + observedMemberCount);
            }
        }
        Integer observedChildCount = null;
        if (expectedChildCount != null) {
            observedChildCount = observedChildCount(parentLinks, flockEvidence);
            if (observedChildCount == null || observedChildCount != expectedChildCount) {
                failures.add("expected childCount=" + expectedChildCount + " but observed " + observedChildCount);
            }
        }
        Double observedDistance = null;
        if (expectedDistanceBand != null) {
            observedDistance = observedFlockDistance(flockEvidence);
            if (!distanceInBand(observedDistance, expectedDistanceBand)) {
                failures.add("expected distance band=" + expectedDistanceBand + " but observed " + observedDistance);
            }
        }
        addFixtureSelectorFailures(failures, candidates);
        if (expectUnsupported != null) {
            boolean observedUnsupported = candidates.stream().anyMatch(candidate -> candidate.get("unsupportedFields") instanceof List<?> list && !list.isEmpty());
            if (expectUnsupported != observedUnsupported) {
                failures.add("expected unsupported=" + expectUnsupported + " but observed " + observedUnsupported);
            }
        }

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("kind", "flock-evidence");
        if (fixtureId != null) {
            evidence.put("fixtureId", fixtureId);
        }
        if (expectedLeaderFixtureId != null) {
            evidence.put("leaderFixtureId", expectedLeaderFixtureId);
        }
        if (expectedParentFixtureId != null) {
            evidence.put("parentFixtureId", expectedParentFixtureId);
        }
        if (observedMemberCount != null) {
            evidence.put("observedMemberCount", observedMemberCount);
        }
        if (observedChildCount != null) {
            evidence.put("observedChildCount", observedChildCount);
        }
        if (observedDistance != null) {
            evidence.put("observedDistance", observedDistance);
        }
        addFixtureSelectorEvidence(evidence, candidates);
        List<Object> unsupportedFields = candidates.stream()
                .flatMap(candidate -> candidate.get("unsupportedFields") instanceof List<?> list ? list.stream() : java.util.stream.Stream.empty())
                .map(item -> (Object) item)
                .distinct()
                .toList();
        evidence.put("unsupportedFields", unsupportedFields);
        Integer firstTick = candidates.stream().map(NpcRuntimeAssertion::tickOrNull).filter(java.util.Objects::nonNull).min(Integer::compareTo).orElse(null);
        Integer lastTick = candidates.stream().map(NpcRuntimeAssertion::tickOrNull).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(null);
        if (firstTick != null) {
            evidence.put("tick", firstTick);
        }
        return new FlockEvaluation(failures.isEmpty(), failures, evidence, firstTick, lastTick, candidates.size());
    }

    @Nonnull
    private NpcRuntimeAssertionResult evaluateMessage(@Nonnull List<Map<String, Object>> evidenceRecords) {
        List<Map<String, Object>> candidates = evidenceRecords.stream()
                .filter(evidence -> matchesText("message-evidence", evidence.get("kind")))
                .filter(this::matchesSignalFixture)
                .filter(this::withinWindow)
                .toList();
        if (candidates.isEmpty()) {
            return "never".equalsIgnoreCase(window.mode())
                    ? NpcRuntimeAssertionResult.passed(assertionId, kind, "forbidden evidence was not observed", null)
                    : NpcRuntimeAssertionResult.unknown(assertionId, kind, "no matching message evidence was observed");
        }

        SignalEvaluation evaluation = evaluateMessageCandidates(candidates);
        if ("never".equalsIgnoreCase(window.mode())) {
            return evaluation.passed()
                    ? NpcRuntimeAssertionResult.failed(assertionId, kind, "forbidden evidence observed", evaluation.evidence(), evaluation.firstTick(), evaluation.lastTick(), evaluation.matchedCount())
                    : NpcRuntimeAssertionResult.passed(assertionId, kind, "forbidden evidence was not observed", null);
        }
        if (evaluation.passed()) {
            return NpcRuntimeAssertionResult.passed(assertionId, kind, "assertion passed", evaluation.evidence(), evaluation.firstTick(), evaluation.lastTick(), evaluation.matchedCount());
        }
        return NpcRuntimeAssertionResult.failed(assertionId, kind, String.join("; ", evaluation.failures()), evaluation.evidence(), evaluation.firstTick(), evaluation.lastTick(), evaluation.matchedCount());
    }

    @Nonnull
    private SignalEvaluation evaluateMessageCandidates(@Nonnull List<Map<String, Object>> candidates) {
        ArrayList<String> failures = new ArrayList<>();
        List<Map<String, Object>> matching = candidates.stream()
                .filter(candidate -> expectedMessageType == null || matchesText(expectedMessageType, candidate.get("messageType")))
                .filter(candidate -> expectedSenderFixtureId == null || matchesText(expectedSenderFixtureId, candidate.get("senderFixtureId")))
                .filter(candidate -> expectedReceiverFixtureId == null || matchesText(expectedReceiverFixtureId, candidate.get("receiverFixtureId")))
                .filter(candidate -> expectedTargetFixtureId == null || matchesText(expectedTargetFixtureId, candidate.get("targetFixtureId")))
                .toList();
        if (matching.isEmpty()) {
            failures.add("no message evidence matched expected sender, receiver, target, and type");
            matching = candidates;
        }

        Integer observedReceiverCount = null;
        if (expectedReceiverCount != null) {
            observedReceiverCount = distinctStringCount(matching, "receiverFixtureId");
            if (observedReceiverCount != expectedReceiverCount) {
                failures.add("expected receiverCount=" + expectedReceiverCount + " but observed " + observedReceiverCount);
            }
        }
        addFixtureSelectorFailures(failures, matching);
        if (expectUnsupported != null) {
            boolean observedUnsupported = matching.stream().anyMatch(candidate -> candidate.get("unsupportedFields") instanceof List<?> list && !list.isEmpty());
            if (expectUnsupported != observedUnsupported) {
                failures.add("expected unsupported=" + expectUnsupported + " but observed " + observedUnsupported);
            }
        }

        LinkedHashMap<String, Object> evidence = signalEvidence("message-evidence", matching);
        if (expectedMessageType != null) {
            evidence.put("messageType", expectedMessageType);
        }
        if (expectedSenderFixtureId != null) {
            evidence.put("senderFixtureId", expectedSenderFixtureId);
        }
        if (expectedReceiverFixtureId != null) {
            evidence.put("receiverFixtureId", expectedReceiverFixtureId);
        }
        if (expectedTargetFixtureId != null) {
            evidence.put("targetFixtureId", expectedTargetFixtureId);
        }
        if (observedReceiverCount != null) {
            evidence.put("observedReceiverCount", observedReceiverCount);
        }
        addFixtureSelectorEvidence(evidence, matching);
        return signalEvaluation(failures, evidence, matching);
    }

    @Nonnull
    private NpcRuntimeAssertionResult evaluateBeacon(@Nonnull List<Map<String, Object>> evidenceRecords) {
        List<Map<String, Object>> candidates = evidenceRecords.stream()
                .filter(evidence -> matchesText("beacon-evidence", evidence.get("kind")))
                .filter(this::matchesSignalFixture)
                .filter(this::withinWindow)
                .toList();
        if (candidates.isEmpty()) {
            return "never".equalsIgnoreCase(window.mode())
                    ? NpcRuntimeAssertionResult.passed(assertionId, kind, "forbidden evidence was not observed", null)
                    : NpcRuntimeAssertionResult.unknown(assertionId, kind, "no matching beacon evidence was observed");
        }

        SignalEvaluation evaluation = evaluateBeaconCandidates(candidates);
        if ("never".equalsIgnoreCase(window.mode())) {
            return evaluation.passed()
                    ? NpcRuntimeAssertionResult.failed(assertionId, kind, "forbidden evidence observed", evaluation.evidence(), evaluation.firstTick(), evaluation.lastTick(), evaluation.matchedCount())
                    : NpcRuntimeAssertionResult.passed(assertionId, kind, "forbidden evidence was not observed", null);
        }
        if (evaluation.passed()) {
            return NpcRuntimeAssertionResult.passed(assertionId, kind, "assertion passed", evaluation.evidence(), evaluation.firstTick(), evaluation.lastTick(), evaluation.matchedCount());
        }
        return NpcRuntimeAssertionResult.failed(assertionId, kind, String.join("; ", evaluation.failures()), evaluation.evidence(), evaluation.firstTick(), evaluation.lastTick(), evaluation.matchedCount());
    }

    @Nonnull
    private SignalEvaluation evaluateBeaconCandidates(@Nonnull List<Map<String, Object>> candidates) {
        ArrayList<String> failures = new ArrayList<>();
        List<Map<String, Object>> matching = candidates.stream()
                .filter(candidate -> expectedBeaconType == null || matchesText(expectedBeaconType, candidate.get("beaconType")))
                .filter(candidate -> expectedSourceFixtureId == null || matchesText(expectedSourceFixtureId, candidate.get("sourceFixtureId")))
                .filter(candidate -> expectedTargetFixtureId == null || matchesText(expectedTargetFixtureId, candidate.get("targetFixtureId")))
                .toList();
        if (matching.isEmpty()) {
            failures.add("no beacon evidence matched expected source, target, and type");
            matching = candidates;
        }

        Integer observedConsumerCount = null;
        if (expectedConsumerCount != null) {
            observedConsumerCount = distinctStringCount(matching, "consumerFixtureId");
            if (observedConsumerCount != expectedConsumerCount) {
                failures.add("expected consumerCount=" + expectedConsumerCount + " but observed " + observedConsumerCount);
            }
        }
        addFixtureSelectorFailures(failures, matching);
        if (expectUnsupported != null) {
            boolean observedUnsupported = matching.stream().anyMatch(candidate -> candidate.get("unsupportedFields") instanceof List<?> list && !list.isEmpty());
            if (expectUnsupported != observedUnsupported) {
                failures.add("expected unsupported=" + expectUnsupported + " but observed " + observedUnsupported);
            }
        }

        LinkedHashMap<String, Object> evidence = signalEvidence("beacon-evidence", matching);
        if (expectedBeaconType != null) {
            evidence.put("beaconType", expectedBeaconType);
        }
        if (expectedSourceFixtureId != null) {
            evidence.put("sourceFixtureId", expectedSourceFixtureId);
        }
        if (expectedTargetFixtureId != null) {
            evidence.put("targetFixtureId", expectedTargetFixtureId);
        }
        if (observedConsumerCount != null) {
            evidence.put("observedConsumerCount", observedConsumerCount);
        }
        addFixtureSelectorEvidence(evidence, matching);
        return signalEvaluation(failures, evidence, matching);
    }

    @Nonnull
    private static LinkedHashMap<String, Object> signalEvidence(@Nonnull String kind,
                                                                 @Nonnull List<Map<String, Object>> matching) {
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("kind", kind);
        List<Object> unsupportedFields = matching.stream()
                .flatMap(candidate -> candidate.get("unsupportedFields") instanceof List<?> list ? list.stream() : java.util.stream.Stream.empty())
                .map(item -> (Object) item)
                .distinct()
                .toList();
        evidence.put("unsupportedFields", unsupportedFields);
        return evidence;
    }

    @Nonnull
    private static SignalEvaluation signalEvaluation(@Nonnull List<String> failures,
                                                     @Nonnull LinkedHashMap<String, Object> evidence,
                                                     @Nonnull List<Map<String, Object>> matching) {
        Integer firstTick = matching.stream().map(NpcRuntimeAssertion::tickOrNull).filter(java.util.Objects::nonNull).min(Integer::compareTo).orElse(null);
        Integer lastTick = matching.stream().map(NpcRuntimeAssertion::tickOrNull).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(null);
        if (firstTick != null) {
            evidence.put("tick", firstTick);
        }
        return new SignalEvaluation(failures.isEmpty(), failures, evidence, firstTick, lastTick, matching.size());
    }

    private boolean matchesSignalFixture(@Nonnull Map<String, Object> evidence) {
        return fixtureId == null || matchesText(fixtureId, evidenceFixtureId(evidence));
    }

    private static int distinctStringCount(@Nonnull List<Map<String, Object>> candidates, @Nonnull String field) {
        return Math.toIntExact(candidates.stream()
                .map(candidate -> candidate.get(field))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> !value.isBlank())
                .distinct()
                .count());
    }

    private boolean leaderMatches(@Nonnull List<Map<String, Object>> flockLinks,
                                  @Nonnull List<Map<String, Object>> flockEvidence) {
        if (fixtureId != null && matchesText(expectedLeaderFixtureId, fixtureId)) {
            return true;
        }
        if (flockEvidence.stream().anyMatch(evidence -> {
            if (fixtureId != null && !matchesText(fixtureId, evidenceFixtureId(evidence))) {
                return false;
            }
            return matchesText(expectedLeaderFixtureId, evidence.get("leaderFixtureId"));
        })) {
            return true;
        }
        return flockLinks.stream().anyMatch(link -> {
            if (fixtureId != null && !matchesText(fixtureId, evidenceFixtureId(link))) {
                return false;
            }
            return matchesText(expectedLeaderFixtureId, link.get("targetFixtureId"));
        });
    }

    private boolean parentMatches(@Nonnull List<Map<String, Object>> parentLinks,
                                  @Nonnull List<Map<String, Object>> flockEvidence) {
        return flockEvidence.stream().anyMatch(evidence -> {
            if (fixtureId != null && !matchesText(fixtureId, evidenceFixtureId(evidence))) {
                return false;
            }
            return matchesText(expectedParentFixtureId, evidence.get("parentFixtureId"));
        }) || parentLinks.stream().anyMatch(link -> {
            if (fixtureId != null && !matchesText(fixtureId, evidenceFixtureId(link))) {
                return false;
            }
            return matchesText(expectedParentFixtureId, link.get("targetFixtureId"));
        });
    }

    @Nullable
    private Double observedFlockDistance(@Nonnull List<Map<String, Object>> flockEvidence) {
        return flockEvidence.stream()
                .filter(evidence -> fixtureId == null || matchesText(fixtureId, evidenceFixtureId(evidence)))
                .map(evidence -> {
                    Object distance = expectedParentFixtureId != null
                            ? evidence.get("distanceToParent")
                            : evidence.get("distanceToLeader");
                    if (!(distance instanceof Number)) {
                        distance = evidence.get("distance");
                    }
                    return distance instanceof Number number ? number.doubleValue() : null;
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static boolean distanceInBand(@Nullable Double observedDistance, @Nonnull List<Object> band) {
        if (observedDistance == null || band.isEmpty()) {
            return false;
        }
        Double min = null;
        Double max = null;
        if (band.size() == 1 && band.getFirst() instanceof Number number) {
            max = number.doubleValue();
        } else {
            if (band.get(0) instanceof Number number) {
                min = number.doubleValue();
            }
            if (band.size() > 1 && band.get(1) instanceof Number number) {
                max = number.doubleValue();
            }
        }
        if (min != null && observedDistance < min) {
            return false;
        }
        return max == null || observedDistance <= max;
    }

    private void addFixtureSelectorFailures(@Nonnull List<String> failures,
                                            @Nonnull List<Map<String, Object>> candidates) {
        List<String> observedFixtureIds = observedFixtureIds(candidates);
        List<String> required = stringList(allFixtures);
        if (!required.isEmpty() && !observedFixtureIds.containsAll(required)) {
            ArrayList<String> missing = new ArrayList<>(required);
            missing.removeAll(observedFixtureIds);
            failures.add("missing required fixture evidence " + missing);
        }
        List<String> alternatives = stringList(anyFixture);
        if (!alternatives.isEmpty() && alternatives.stream().noneMatch(observedFixtureIds::contains)) {
            failures.add("none of anyFixture alternatives were observed " + alternatives);
        }
    }

    private void addFixtureSelectorEvidence(@Nonnull Map<String, Object> evidence,
                                            @Nonnull List<Map<String, Object>> candidates) {
        List<String> observedFixtureIds = observedFixtureIds(candidates);
        if (!observedFixtureIds.isEmpty()) {
            evidence.put("observedFixtureIds", observedFixtureIds);
        }
        List<String> required = stringList(allFixtures);
        if (!required.isEmpty()) {
            evidence.put("allFixtures", required);
        }
        List<String> alternatives = stringList(anyFixture);
        if (!alternatives.isEmpty()) {
            evidence.put("anyFixture", alternatives);
        }
    }

    @Nonnull
    private static List<String> observedFixtureIds(@Nonnull List<Map<String, Object>> candidates) {
        ArrayList<String> ids = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            addObservedFixtureId(ids, evidenceFixtureId(candidate));
            addObservedFixtureId(ids, stringValue(candidate.get("receiverFixtureId")));
            addObservedFixtureId(ids, stringValue(candidate.get("consumerFixtureId")));
            addObservedFixtureId(ids, stringValue(candidate.get("targetFixtureId")));
            addObservedFixtureId(ids, stringValue(candidate.get("sourceFixtureId")));
            addObservedFixtureId(ids, stringValue(candidate.get("senderFixtureId")));
        }
        return List.copyOf(ids);
    }

    private static void addObservedFixtureId(@Nonnull List<String> ids, @Nullable String fixtureId) {
        if (fixtureId != null && !fixtureId.isBlank() && !ids.contains(fixtureId)) {
            ids.add(fixtureId);
        }
    }

    @Nullable
    private Integer observedMemberCount(@Nonnull List<Map<String, Object>> flockLinks,
                                        @Nonnull List<Map<String, Object>> flockEvidence) {
        Integer evidenceCount = flockEvidence.stream()
                .filter(evidence -> fixtureId == null || matchesText(fixtureId, evidenceFixtureId(evidence)))
                .map(evidence -> intOrNull(evidence.get("memberCount")))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (evidenceCount != null) {
            return evidenceCount;
        }
        if (fixtureId == null) {
            return null;
        }
        long followers = flockLinks.stream()
                .filter(link -> matchesText(fixtureId, link.get("targetFixtureId")))
                .map(NpcRuntimeAssertion::evidenceFixtureId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        return Math.toIntExact(followers + 1);
    }

    @Nullable
    private Integer observedChildCount(@Nonnull List<Map<String, Object>> parentLinks,
                                       @Nonnull List<Map<String, Object>> flockEvidence) {
        Integer evidenceCount = flockEvidence.stream()
                .filter(evidence -> fixtureId == null || matchesText(fixtureId, evidence.get("parentFixtureId")))
                .map(evidence -> intOrNull(evidence.get("childCount")))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (evidenceCount != null) {
            return evidenceCount;
        }
        if (fixtureId == null) {
            return null;
        }
        long children = parentLinks.stream()
                .filter(link -> matchesText(fixtureId, link.get("targetFixtureId")))
                .map(NpcRuntimeAssertion::evidenceFixtureId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        return Math.toIntExact(children);
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
        if (expectedEventKind != null && !matchesText(expectedEventKind, evidence.get("kind"))) {
            return false;
        }
        if (!matchesEvidenceKind(evidence)) {
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
        if (fixtureId != null && !matchesText(fixtureId, evidenceFixtureId(evidence))) {
            return false;
        }
        return tameworkField == null || matchesText(tameworkField, evidence.get("field"));
    }

    private boolean withinWindow(@Nonnull Map<String, Object> evidence) {
        Integer tick = tickOrNull(evidence);
        if (tick == null || tick < window.startTick() || tick > window.endTick()) {
            return tick == null;
        }
        return withinDeliveryWindowTicks == null || tick <= window.startTick() + withinDeliveryWindowTicks;
    }

    private boolean supportedKind() {
        return "sensor".equalsIgnoreCase(kind)
                || "action".equalsIgnoreCase(kind)
                || "combat".equalsIgnoreCase(kind)
                || "combat-evaluator".equalsIgnoreCase(kind)
                || "tamework".equalsIgnoreCase(kind)
                || isFlockKind()
                || isMessageKind()
                || isBeaconKind();
    }

    private boolean isFlockKind() {
        return "flock".equalsIgnoreCase(kind) || "family".equalsIgnoreCase(kind);
    }

    private boolean isMessageKind() {
        return "message".equalsIgnoreCase(kind);
    }

    private boolean isBeaconKind() {
        return "beacon".equalsIgnoreCase(kind);
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

    private boolean matchesEvidenceKind(@Nonnull Map<String, Object> evidence) {
        Object observedKind = evidence.get("kind");
        if ("action".equalsIgnoreCase(kind)) {
            return matchesText("action-evidence", observedKind)
                    || matchesText("action-start", observedKind)
                    || matchesText("action-change", observedKind)
                    || matchesText("action-end", observedKind);
        }
        return matchesText(evidenceKind(), observedKind);
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

    @Nullable
    private static Integer intOrNull(@Nullable Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    @Nonnull
    private static List<String> stringList(@Nullable List<Object> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        ArrayList<String> out = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                out.add(text);
            }
        }
        return List.copyOf(out);
    }

    @Nullable
    private static String stringValue(@Nullable Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    @Nullable
    private static String evidenceFixtureId(@Nonnull Map<String, Object> evidence) {
        Object value = evidence.containsKey("fixtureId") ? evidence.get("fixtureId") : evidence.get("fixture");
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static boolean containsValue(@Nullable Object value, @Nonnull String expected) {
        return value instanceof List<?> list && list.stream().anyMatch(item -> matchesText(expected, item));
    }

    @Nullable
    private static Integer tickOrNull(@Nonnull Map<String, Object> evidence) {
        Object tick = evidence.get("tick");
        if (tick instanceof Number number) {
            return number.intValue();
        }
        Object startTick = evidence.get("startTick");
        if (startTick instanceof Number number) {
            return number.intValue();
        }
        Object endTick = evidence.get("endTick");
        return endTick instanceof Number number ? number.intValue() : null;
    }

    private record Evaluation(boolean passed, @Nonnull List<String> failures) {
    }

    private record FlockEvaluation(boolean passed,
                                   @Nonnull List<String> failures,
                                   @Nonnull Map<String, Object> evidence,
                                   @Nullable Integer firstTick,
                                   @Nullable Integer lastTick,
                                   int matchedCount) {
    }

    private record SignalEvaluation(boolean passed,
                                    @Nonnull List<String> failures,
                                    @Nonnull Map<String, Object> evidence,
                                    @Nullable Integer firstTick,
                                    @Nullable Integer lastTick,
                                    int matchedCount) {
    }
}
