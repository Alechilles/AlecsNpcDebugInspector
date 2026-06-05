package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Emits structured runtime observation records from inspector snapshots.
 */
public final class NpcRuntimeObserver {
    private final NpcRuntimeSensorObserver sensorObserver = new NpcRuntimeSensorObserver();
    private final NpcRuntimeActionObserver actionObserver = new NpcRuntimeActionObserver();
    private final NpcRuntimeTameworkObserver tameworkObserver = new NpcRuntimeTameworkObserver();
    private final NpcRuntimeEngineHookObserver engineHookObserver = new NpcRuntimeEngineHookObserver();

    @Nonnull
    public NpcRuntimeObservedNpc observe(@Nullable UUID npcUuid, @Nonnull NpcDebugSnapshot snapshot) {
        return NpcRuntimeObservedNpc.fromSnapshot(npcUuid, snapshot);
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous) {
        return traceRecords(
                requestId,
                tick,
                current,
                previous,
                new NpcRuntimeRequest.EngineHooksSpec(false, false, false, false),
                current.npcUuid() != null ? current.npcUuid().toString() : "npc_under_test"
        );
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId) {
        return traceRecords(requestId, tick, current, previous, engineHooks, npcId, new NpcRuntimeActionObserver.ActionLifecycleTracker());
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeActionObserver.ActionLifecycleTracker actionLifecycleTracker) {
        return traceRecords(requestId, tick, current, previous, engineHooks, npcId, actionLifecycleTracker, List.of());
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeActionObserver.ActionLifecycleTracker actionLifecycleTracker,
                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        return traceRecords(requestId, tick, current, previous, engineHooks, npcId, actionLifecycleTracker, fixtures, new NpcRuntimeFixtureRegistry());
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeActionObserver.ActionLifecycleTracker actionLifecycleTracker,
                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                                    @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry) {
        Map<String, NpcRuntimeObservedNpc> currentByFixture = Map.of(npcId, current);
        Map<String, NpcRuntimeObservedNpc> previousByFixture = previous != null ? Map.of(npcId, previous) : Map.of();
        return traceRecords(
                requestId,
                tick,
                current,
                previous,
                engineHooks,
                npcId,
                actionLifecycleTracker,
                fixtures,
                fixtureRegistry,
                currentByFixture,
                previousByFixture
        );
    }

    @Nonnull
    public List<NpcRuntimeTraceRecord> traceRecords(@Nonnull String requestId,
                                                    int tick,
                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                    @Nonnull NpcRuntimeRequest.EngineHooksSpec engineHooks,
                                                    @Nonnull String npcId,
                                                    @Nonnull NpcRuntimeActionObserver.ActionLifecycleTracker actionLifecycleTracker,
                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                                    @Nonnull NpcRuntimeFixtureRegistry fixtureRegistry,
                                                    @Nonnull Map<String, NpcRuntimeObservedNpc> currentByFixture,
                                                    @Nonnull Map<String, NpcRuntimeObservedNpc> previousByFixture) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        records.add(record(requestId, tick, "npc-state", current.stateMap()));
        addSection(records, requestId, tick, "targeting", current.section("Targeting / Sensors"));
        addSection(records, requestId, tick, "timers", current.section("Timers / Cooldowns"));
        addSection(records, requestId, tick, "pathing", current.section("Pathing"));
        addSection(records, requestId, tick, "combat", current.section("Combat"));
        addSection(records, requestId, tick, "inventory", current.section("Inventory / Equipment"));
        addSection(records, requestId, tick, "alarms", current.section("Alarms"));
        addSection(records, requestId, tick, "flags", current.section("Flags"));
        addSection(records, requestId, tick, "components", current.section("Components"));
        addSection(records, requestId, tick, "flock", current.section("Flock"));
        addSection(records, requestId, tick, "recent-events", current.section("Recent Events"));
        records.add(signalSurfaceRecord(requestId, tick, current));
        records.addAll(flockEvidenceRecords(requestId, tick, current, previous, fixtures, currentByFixture, previousByFixture));
        records.addAll(signalEvidenceRecords(requestId, tick, current, previous, fixtures, currentByFixture, previousByFixture));
        records.addAll(sensorObserver.traceRecords(requestId, tick, current, fixtures));
        records.addAll(actionObserver.traceRecords(requestId, tick, current, previous, actionLifecycleTracker));
        Map<String, Object> tamework = current.tameworkMap();
        if (!tamework.isEmpty()) {
            records.add(record(requestId, tick, "tamework", tamework));
            records.addAll(tameworkObserver.traceRecords(requestId, tick, tamework));
        }
        records.addAll(engineHookObserver.traceRecords(requestId, tick, npcId, current, previous, engineHooks, fixtureRegistry, fixtures));

        NpcRuntimeObservationDiff diff = NpcRuntimeObservationDiff.between(previous, current);
        if (diff.changed()) {
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "npc-transition")
                    .with("changeCount", diff.changes().size())
                    .with("changes", diff.changes().stream().map(NpcRuntimeObserver::changeMap).toList()));
        }
        return records;
    }

    @Nonnull
    private static NpcRuntimeTraceRecord signalSurfaceRecord(@Nonnull String requestId,
                                                             int tick,
                                                             @Nonnull NpcRuntimeObservedNpc current) {
        LinkedHashMap<String, Object> exposed = new LinkedHashMap<>();
        for (String section : signalSectionNames()) {
            Map<String, String> fields = current.section(section);
            exposed.put(section, fields.isEmpty() ? "absent" : fields.keySet());
        }
        return NpcRuntimeTraceRecord.of(requestId, tick, "signal-surface")
                .with("messageSourceSections", List.of("Targeting / Sensors", "Timers / Cooldowns", "Flock", "Recent Events"))
                .with("beaconSourceSections", List.of("Targeting / Sensors", "Timers / Cooldowns", "Flock", "Recent Events"))
                .with("sharedTargetSlotSourceSections", List.of("Targeting / Sensors"))
                .with("flockAlertSourceSections", List.of("Flock", "Recent Events"))
                .with("relatedTimerSourceSections", List.of("Timers / Cooldowns"))
                .with("exposedSections", exposed)
                .with("unsupportedFields", List.of(
                        "enginePrivateMessageQueue",
                        "enginePrivateBeaconBus",
                        "enginePrivateDeliveryOrdering",
                        "directMessageMutation",
                        "directBeaconMutation"
                ));
    }

    @Nonnull
    private static List<NpcRuntimeTraceRecord> signalEvidenceRecords(@Nonnull String requestId,
                                                                     int tick,
                                                                     @Nonnull NpcRuntimeObservedNpc current,
                                                                     @Nullable NpcRuntimeObservedNpc previous,
                                                                     @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                                                     @Nonnull Map<String, NpcRuntimeObservedNpc> currentByFixture,
                                                                     @Nonnull Map<String, NpcRuntimeObservedNpc> previousByFixture) {
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        Map<String, NpcRuntimeFixtureSpec> fixtureById = new LinkedHashMap<>();
        for (NpcRuntimeFixtureSpec fixture : fixtures) {
            fixtureById.put(fixture.fixtureId(), fixture);
        }
        for (NpcRuntimeFixtureSpec fixture : fixtures) {
            if (fixture.kind() == NpcRuntimeFixtureKind.MESSAGE) {
                addMessageEvidence(records, requestId, tick, current, previous, fixture, currentByFixture, previousByFixture);
            } else if (fixture.kind() == NpcRuntimeFixtureKind.BEACON) {
                addBeaconEvidence(records, requestId, tick, current, previous, fixture, currentByFixture, previousByFixture, fixtureById);
            }
        }
        return records;
    }

    private static void addMessageEvidence(@Nonnull List<NpcRuntimeTraceRecord> records,
                                           @Nonnull String requestId,
                                           int tick,
                                           @Nonnull NpcRuntimeObservedNpc current,
                                           @Nullable NpcRuntimeObservedNpc previous,
                                           @Nonnull NpcRuntimeFixtureSpec fixture,
                                           @Nonnull Map<String, NpcRuntimeObservedNpc> currentByFixture,
                                           @Nonnull Map<String, NpcRuntimeObservedNpc> previousByFixture) {
        SignalSnapshot signal = messageSnapshot(current, fixture, currentByFixture);
        if (signal.observed()) {
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "message-evidence")
                    .with("fixtureId", fixture.fixtureId())
                    .with("messageId", fixture.messageId())
                    .with("messageType", fixture.messageType())
                    .with("senderFixtureId", fixture.senderFixtureId())
                    .with("receiverFixtureId", fixture.receiverFixtureId())
                    .with("targetFixtureId", fixture.targetFixtureId())
                    .with("targetSlot", fixture.targetSlot())
                    .with("payloadKeys", fixture.payloadKeys())
                    .with("observedValue", signal.observedValue())
                    .with("sourceSections", signal.sourceSections())
                    .with("observedFields", signal.fields())
                    .with("unsupportedFields", List.of()));
        }

        SignalSnapshot previousSignal = messageSnapshot(previousForSignal(previous, fixture, previousByFixture), fixture, previousByFixture);
        addSignalTransition(records, requestId, tick, "message-transition", fixture, previousSignal, signal);
    }

    private static void addBeaconEvidence(@Nonnull List<NpcRuntimeTraceRecord> records,
                                          @Nonnull String requestId,
                                          int tick,
                                          @Nonnull NpcRuntimeObservedNpc current,
                                          @Nullable NpcRuntimeObservedNpc previous,
                                          @Nonnull NpcRuntimeFixtureSpec fixture,
                                          @Nonnull Map<String, NpcRuntimeObservedNpc> currentByFixture,
                                          @Nonnull Map<String, NpcRuntimeObservedNpc> previousByFixture,
                                          @Nonnull Map<String, NpcRuntimeFixtureSpec> fixtureById) {
        SignalSnapshot signal = beaconSnapshot(current, fixture, currentByFixture);
        if (signal.observed()) {
            List<Object> consumers = fixture.requiredConsumerFixtureIds().isEmpty()
                    ? List.of((Object) null)
                    : fixture.requiredConsumerFixtureIds();
            for (Object consumer : consumers) {
                records.add(NpcRuntimeTraceRecord.of(requestId, tick, "beacon-evidence")
                        .with("fixtureId", fixture.fixtureId())
                        .with("beaconId", fixture.beaconId())
                        .with("beaconType", fixture.beaconType())
                        .with("sourceFixtureId", fixture.sourceFixtureId())
                        .with("targetFixtureId", fixture.targetFixtureId())
                        .with("consumerFixtureId", consumer instanceof String text ? text : null)
                        .with("position", fixture.position())
                        .with("radius", fixture.radius())
                        .with("ttlTicks", fixture.ttlTicks())
                        .with("lifecycle", beaconLifecycle(signal))
                        .with("observedValue", signal.observedValue())
                        .with("sourceSections", signal.sourceSections())
                        .with("observedFields", signal.fields())
                        .with("distanceToTarget", distanceToFixture(fixture, fixture.targetFixtureId(), fixtureById))
                        .with("unsupportedFields", List.of()));
            }
        }

        SignalSnapshot previousSignal = beaconSnapshot(previousForSignal(previous, fixture, previousByFixture), fixture, previousByFixture);
        addSignalTransition(records, requestId, tick, "beacon-transition", fixture, previousSignal, signal);
    }

    @Nullable
    private static NpcRuntimeObservedNpc previousForSignal(@Nullable NpcRuntimeObservedNpc previous,
                                                           @Nonnull NpcRuntimeFixtureSpec fixture,
                                                           @Nonnull Map<String, NpcRuntimeObservedNpc> previousByFixture) {
        if (fixture.receiverFixtureId() != null && previousByFixture.containsKey(fixture.receiverFixtureId())) {
            return previousByFixture.get(fixture.receiverFixtureId());
        }
        if (fixture.sourceFixtureId() != null && previousByFixture.containsKey(fixture.sourceFixtureId())) {
            return previousByFixture.get(fixture.sourceFixtureId());
        }
        if (fixture.senderFixtureId() != null && previousByFixture.containsKey(fixture.senderFixtureId())) {
            return previousByFixture.get(fixture.senderFixtureId());
        }
        return previous;
    }

    @Nonnull
    private static SignalSnapshot messageSnapshot(@Nullable NpcRuntimeObservedNpc fallback,
                                                  @Nonnull NpcRuntimeFixtureSpec fixture,
                                                  @Nonnull Map<String, NpcRuntimeObservedNpc> observedByFixture) {
        NpcRuntimeObservedNpc sender = fixture.senderFixtureId() != null
                ? observedByFixture.get(fixture.senderFixtureId())
                : null;
        NpcRuntimeObservedNpc receiver = fixture.receiverFixtureId() != null
                ? observedByFixture.get(fixture.receiverFixtureId())
                : null;
        ArrayList<String> tokens = new ArrayList<>(List.of("message", "broadcast", "alert", "signal"));
        addToken(tokens, fixture.messageId());
        addToken(tokens, fixture.messageType());
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        collectSignalFields(fields, sender != null ? sender : fallback, "sender", tokens);
        collectSignalFields(fields, receiver != null ? receiver : fallback, "receiver", tokens);
        boolean targetSlotObserved = fixture.targetSlot() != null
                && receiver != null
                && hasVisibleTargetSlot(receiver, fixture.targetSlot());
        if (targetSlotObserved) {
            fields.put("receiver.Targeting / Sensors.targetSlot." + fixture.targetSlot(), receiver.section("Targeting / Sensors").toString());
        }
        return SignalSnapshot.fromFields(fields, targetSlotObserved ? "target-slot-observed" : null);
    }

    @Nonnull
    private static SignalSnapshot beaconSnapshot(@Nullable NpcRuntimeObservedNpc fallback,
                                                 @Nonnull NpcRuntimeFixtureSpec fixture,
                                                 @Nonnull Map<String, NpcRuntimeObservedNpc> observedByFixture) {
        NpcRuntimeObservedNpc source = fixture.sourceFixtureId() != null
                ? observedByFixture.get(fixture.sourceFixtureId())
                : null;
        ArrayList<String> tokens = new ArrayList<>(List.of("beacon"));
        addToken(tokens, fixture.beaconId());
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        collectSignalFields(fields, source != null ? source : fallback, "source", tokens);
        for (Object consumer : fixture.requiredConsumerFixtureIds()) {
            if (consumer instanceof String consumerFixtureId) {
                collectSignalFields(fields, observedByFixture.get(consumerFixtureId), "consumer." + consumerFixtureId, tokens);
            }
        }
        return SignalSnapshot.fromFields(fields, null);
    }

    private static void collectSignalFields(@Nonnull Map<String, String> fields,
                                            @Nullable NpcRuntimeObservedNpc observed,
                                            @Nonnull String prefix,
                                            @Nonnull List<String> tokens) {
        if (observed == null) {
            return;
        }
        for (String section : signalSectionNames()) {
            for (Map.Entry<String, String> entry : observed.section(section).entrySet()) {
                if (matchesAnySignalToken(entry.getKey(), entry.getValue(), tokens)) {
                    fields.put(prefix + "." + section + "." + entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private static boolean hasVisibleTargetSlot(@Nonnull NpcRuntimeObservedNpc observed, @Nonnull String targetSlot) {
        String expectedKey = NpcRuntimeObservedNpc.normalizeFieldName("Target " + targetSlot);
        String value = observed.section("Targeting / Sensors").get(expectedKey);
        return value != null && !value.isBlank() && !"<none>".equalsIgnoreCase(value);
    }

    private static boolean matchesAnySignalToken(@Nonnull String key,
                                                 @Nonnull String value,
                                                 @Nonnull List<String> tokens) {
        String searchable = (key + " " + value).toLowerCase(Locale.ROOT);
        String compactSearchable = compactToken(searchable);
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            if (searchable.contains(normalized) || compactSearchable.contains(compactToken(normalized))) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String compactToken(@Nonnull String value) {
        return value.replaceAll("[^a-z0-9]+", "");
    }

    private static void addToken(@Nonnull List<String> tokens, @Nullable String token) {
        if (token != null && !token.isBlank()) {
            tokens.add(token);
        }
    }

    @Nonnull
    private static List<String> signalSectionNames() {
        return List.of("Targeting / Sensors", "Timers / Cooldowns", "Flock", "Recent Events");
    }

    private static void addSignalTransition(@Nonnull List<NpcRuntimeTraceRecord> records,
                                            @Nonnull String requestId,
                                            int tick,
                                            @Nonnull String kind,
                                            @Nonnull NpcRuntimeFixtureSpec fixture,
                                            @Nonnull SignalSnapshot previous,
                                            @Nonnull SignalSnapshot current) {
        List<Map<String, Object>> changes = signalChanges(previous.fields(), current.fields());
        if (changes.isEmpty()) {
            return;
        }
        NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(requestId, tick, kind)
                .with("fixtureId", fixture.fixtureId())
                .with("messageId", fixture.messageId())
                .with("messageType", fixture.messageType())
                .with("senderFixtureId", fixture.senderFixtureId())
                .with("receiverFixtureId", fixture.receiverFixtureId())
                .with("beaconId", fixture.beaconId())
                .with("beaconType", fixture.beaconType())
                .with("sourceFixtureId", fixture.sourceFixtureId())
                .with("targetFixtureId", fixture.targetFixtureId())
                .with("targetSlot", fixture.targetSlot())
                .with("lifecycle", signalLifecycle(previous, current))
                .with("changeCount", changes.size())
                .with("changes", changes)
                .with("unsupportedFields", List.of());
        records.add(record);
    }

    @Nonnull
    private static String signalLifecycle(@Nonnull SignalSnapshot previous, @Nonnull SignalSnapshot current) {
        if (!previous.observed() && current.observed()) {
            return "appeared";
        }
        if (previous.observed() && !current.observed()) {
            return "disappeared";
        }
        return "changed";
    }

    @Nonnull
    private static String beaconLifecycle(@Nonnull SignalSnapshot signal) {
        String text = signal.observedValue().toLowerCase(Locale.ROOT);
        if (text.contains("consume")) {
            return "consumed";
        }
        if (text.contains("expire")) {
            return "expired";
        }
        if (text.contains("move")) {
            return "moved";
        }
        return "observed";
    }

    @Nonnull
    private static List<Map<String, Object>> signalChanges(@Nonnull Map<String, String> before,
                                                           @Nonnull Map<String, String> after) {
        ArrayList<Map<String, Object>> changes = new ArrayList<>();
        for (String field : unionKeys(before, after)) {
            Object beforeValue = before.get(field);
            Object afterValue = after.get(field);
            if (!java.util.Objects.equals(beforeValue, afterValue)) {
                LinkedHashMap<String, Object> change = new LinkedHashMap<>();
                change.put("field", field);
                change.put("before", beforeValue);
                change.put("after", afterValue);
                changes.add(change);
            }
        }
        return List.copyOf(changes);
    }

    @Nonnull
    private static List<NpcRuntimeTraceRecord> flockEvidenceRecords(@Nonnull String requestId,
                                                                    int tick,
                                                                    @Nonnull NpcRuntimeObservedNpc current,
                                                                    @Nullable NpcRuntimeObservedNpc previous,
                                                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures,
                                                                    @Nonnull Map<String, NpcRuntimeObservedNpc> currentByFixture,
                                                                    @Nonnull Map<String, NpcRuntimeObservedNpc> previousByFixture) {
        List<NpcRuntimeFixtureSpec> npcFixtures = fixtures.stream()
                .filter(fixture -> fixture.kind().entityLike())
                .toList();
        if (npcFixtures.isEmpty()) {
            return List.of();
        }
        ArrayList<NpcRuntimeTraceRecord> records = new ArrayList<>();
        Map<String, NpcRuntimeFixtureSpec> fixtureById = new LinkedHashMap<>();
        for (NpcRuntimeFixtureSpec fixture : fixtures) {
            fixtureById.put(fixture.fixtureId(), fixture);
        }
        for (NpcRuntimeFixtureSpec fixture : npcFixtures) {
            NpcRuntimeObservedNpc observed = currentByFixture.getOrDefault(fixture.fixtureId(), current);
            records.add(flockEvidenceRecord(requestId, tick, observed, fixture, npcFixtures, fixtureById));
        }
        for (NpcRuntimeFixtureSpec fixture : npcFixtures) {
            NpcRuntimeObservedNpc observed = currentByFixture.getOrDefault(fixture.fixtureId(), current);
            NpcRuntimeObservedNpc previousObserved = previousByFixture.get(fixture.fixtureId());
            if (previousObserved == null && fixture.fixtureId().equals("npcUnderTest")) {
                previousObserved = previous;
            }
            List<Map<String, Object>> flockChanges = flockChanges(previousObserved, observed);
            if (!flockChanges.isEmpty()) {
                records.add(NpcRuntimeTraceRecord.of(requestId, tick, "flock-transition")
                        .with("fixtureId", fixture.fixtureId())
                        .with("roleId", fixture.roleId())
                        .with("flockId", fixture.flockId())
                        .with("leaderFixtureId", fixture.leaderFixtureId())
                        .with("familyId", fixture.familyId())
                        .with("parentFixtureId", fixture.parentFixtureId())
                        .with("changeCount", flockChanges.size())
                        .with("changes", flockChanges)
                        .with("unsupportedFields", unsupportedFlockFields(fixture)));
            }
        }
        return records;
    }

    @Nonnull
    private static NpcRuntimeTraceRecord flockEvidenceRecord(@Nonnull String requestId,
                                                             int tick,
                                                             @Nonnull NpcRuntimeObservedNpc current,
                                                             @Nonnull NpcRuntimeFixtureSpec fixture,
                                                             @Nonnull List<NpcRuntimeFixtureSpec> npcFixtures,
                                                             @Nonnull Map<String, NpcRuntimeFixtureSpec> fixtureById) {
        boolean leader = isLeader(fixture, npcFixtures);
        String leaderFixtureId = leaderFixtureId(fixture, leader);
        String parentFixtureId = parentFixtureId(fixture);
        int memberCount = memberCount(fixture, leaderFixtureId, npcFixtures);
        int childCount = childCount(fixture, parentFixtureId, npcFixtures);
        List<String> missingFixtureIds = missingFixtureIds(fixture, fixtureById);
        NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(requestId, tick, "flock-evidence")
                .with("fixtureId", fixture.fixtureId())
                .with("roleId", fixture.roleId())
                .with("flockId", fixture.flockId())
                .with("flockRole", fixture.flockRole())
                .with("familyId", fixture.familyId())
                .with("familyRole", fixture.familyRole())
                .with("leaderFixtureId", leaderFixtureId)
                .with("parentFixtureId", parentFixtureId)
                .with("memberCount", memberCount)
                .with("childCount", childCount)
                .with("isLeader", leader)
                .with("distanceToLeader", distanceToFixture(fixture, leaderFixtureId, fixtureById))
                .with("distanceToParent", distanceToFixture(fixture, parentFixtureId, fixtureById))
                .with("state", current.aiState())
                .with("substate", current.aiSubstate())
                .with("currentInstruction", current.currentInstruction())
                .with("observedMemberCount", memberCount)
                .with("expectedMemberCount", memberCount)
                .with("missingFixtureIds", missingFixtureIds)
                .with("extraFixtureIds", List.of())
                .with("unsupportedFields", unsupportedFlockFields(fixture));
        return record;
    }

    @Nullable
    private static String leaderFixtureId(@Nonnull NpcRuntimeFixtureSpec fixture, boolean isLeader) {
        return fixture.leaderFixtureId() != null ? fixture.leaderFixtureId() : isLeader ? fixture.fixtureId() : null;
    }

    @Nullable
    private static String parentFixtureId(@Nonnull NpcRuntimeFixtureSpec fixture) {
        return fixture.parentFixtureId();
    }

    private static boolean isLeader(@Nonnull NpcRuntimeFixtureSpec fixture,
                                    @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        return "leader".equalsIgnoreCase(String.valueOf(fixture.flockRole()))
                || fixtures.stream().anyMatch(candidate -> fixture.fixtureId().equals(candidate.leaderFixtureId()));
    }

    private static int memberCount(@Nonnull NpcRuntimeFixtureSpec fixture,
                                   @Nullable String leaderFixtureId,
                                   @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        String leader = leaderFixtureId != null ? leaderFixtureId : fixture.fixtureId();
        long linkedFollowers = fixtures.stream()
                .filter(candidate -> leader.equals(candidate.leaderFixtureId()))
                .count();
        boolean leaderFixturePresent = fixtures.stream().anyMatch(candidate -> leader.equals(candidate.fixtureId()));
        if (linkedFollowers > 0 || leaderFixturePresent) {
            return Math.toIntExact(linkedFollowers + (leaderFixturePresent ? 1 : 0));
        }
        return 1;
    }

    private static int childCount(@Nonnull NpcRuntimeFixtureSpec fixture,
                                  @Nullable String parentFixtureId,
                                  @Nonnull List<NpcRuntimeFixtureSpec> fixtures) {
        String parent = parentFixtureId != null ? parentFixtureId : fixture.fixtureId();
        long linkedChildren = fixtures.stream()
                .filter(candidate -> parent.equals(candidate.parentFixtureId()))
                .count();
        return Math.toIntExact(linkedChildren);
    }

    @Nullable
    private static Double distanceToFixture(@Nonnull NpcRuntimeFixtureSpec fixture,
                                            @Nullable String targetFixtureId,
                                            @Nonnull Map<String, NpcRuntimeFixtureSpec> fixtureById) {
        if (targetFixtureId == null || targetFixtureId.equals(fixture.fixtureId())) {
            return null;
        }
        NpcRuntimeFixtureSpec target = fixtureById.get(targetFixtureId);
        if (target == null) {
            return null;
        }
        return distance(fixture.position(), target.position());
    }

    @Nullable
    private static Double distance(@Nonnull List<Object> first, @Nonnull List<Object> second) {
        if (first.size() != 3 || second.size() != 3) {
            return null;
        }
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            if (!(first.get(i) instanceof Number firstNumber) || !(second.get(i) instanceof Number secondNumber)) {
                return null;
            }
            double delta = firstNumber.doubleValue() - secondNumber.doubleValue();
            sum += delta * delta;
        }
        return Math.sqrt(sum);
    }

    @Nonnull
    private static List<String> unsupportedFlockFields(@Nonnull NpcRuntimeFixtureSpec fixture) {
        ArrayList<String> unsupported = new ArrayList<>();
        if (fixture.flockId() != null || fixture.flockRole() != null || fixture.leaderFixtureId() != null) {
            unsupported.add("enginePrivateFlockMembership");
        }
        if (fixture.familyId() != null || fixture.familyRole() != null || fixture.parentFixtureId() != null) {
            unsupported.add("enginePrivateFamilyBinding");
        }
        return List.copyOf(unsupported);
    }

    @Nonnull
    private static List<String> missingFixtureIds(@Nonnull NpcRuntimeFixtureSpec fixture,
                                                  @Nonnull Map<String, NpcRuntimeFixtureSpec> fixtureById) {
        ArrayList<String> missing = new ArrayList<>();
        addMissing(fixture.leaderFixtureId(), fixtureById, missing);
        addMissing(fixture.parentFixtureId(), fixtureById, missing);
        return List.copyOf(missing);
    }

    private static void addMissing(@Nullable String fixtureId,
                                   @Nonnull Map<String, NpcRuntimeFixtureSpec> fixtureById,
                                   @Nonnull List<String> missing) {
        if (fixtureId != null && !fixtureId.isBlank() && !fixtureById.containsKey(fixtureId) && !missing.contains(fixtureId)) {
            missing.add(fixtureId);
        }
    }

    @Nonnull
    private static List<Map<String, Object>> flockChanges(@Nullable NpcRuntimeObservedNpc previous,
                                                          @Nonnull NpcRuntimeObservedNpc current) {
        if (previous == null) {
            return List.of();
        }
        Map<String, String> before = previous.section("Flock");
        Map<String, String> after = current.section("Flock");
        ArrayList<Map<String, Object>> changes = new ArrayList<>();
        for (String field : unionKeys(before, after)) {
            Object beforeValue = before.get(field);
            Object afterValue = after.get(field);
            if (!java.util.Objects.equals(beforeValue, afterValue)) {
                LinkedHashMap<String, Object> change = new LinkedHashMap<>();
                change.put("section", "Flock");
                change.put("field", field);
                change.put("before", beforeValue);
                change.put("after", afterValue);
                changes.add(change);
            }
        }
        return List.copyOf(changes);
    }

    @Nonnull
    private static List<String> unionKeys(@Nonnull Map<String, String> first,
                                          @Nonnull Map<String, String> second) {
        ArrayList<String> keys = new ArrayList<>();
        for (String key : first.keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        for (String key : second.keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Nonnull
    private static Map<String, Object> changeMap(@Nonnull NpcRuntimeObservationDiff.Change change) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("section", change.section());
        map.put("field", change.field());
        map.put("before", change.before());
        map.put("after", change.after());
        return map;
    }

    private static void addSection(@Nonnull List<NpcRuntimeTraceRecord> records,
                                   @Nonnull String requestId,
                                   int tick,
                                   @Nonnull String eventKind,
                                   @Nonnull Map<String, String> values) {
        if (!values.isEmpty()) {
            records.add(record(requestId, tick, eventKind, Map.of("fields", values)));
        }
    }

    @Nonnull
    private static NpcRuntimeTraceRecord record(@Nonnull String requestId,
                                                int tick,
                                                @Nonnull String eventKind,
                                                @Nonnull Map<String, ?> values) {
        NpcRuntimeTraceRecord record = NpcRuntimeTraceRecord.of(requestId, tick, eventKind);
        values.forEach(record::with);
        return record;
    }

    private record SignalSnapshot(@Nonnull Map<String, String> fields, @Nonnull String observedValue) {
        @Nonnull
        static SignalSnapshot fromFields(@Nonnull Map<String, String> fields, @Nullable String overrideObservedValue) {
            if (fields.isEmpty()) {
                return new SignalSnapshot(Map.of(), "");
            }
            String observedValue = overrideObservedValue;
            if (observedValue == null) {
                observedValue = fields.entrySet().stream()
                        .findFirst()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .orElse("observed");
            }
            return new SignalSnapshot(Map.copyOf(fields), observedValue);
        }

        boolean observed() {
            return !fields.isEmpty();
        }

        @Nonnull
        List<String> sourceSections() {
            ArrayList<String> sections = new ArrayList<>();
            for (String key : fields.keySet()) {
                for (String section : signalSectionNames()) {
                    if (key.contains(section) && !sections.contains(section)) {
                        sections.add(section);
                    }
                }
            }
            return List.copyOf(sections);
        }
    }
}
