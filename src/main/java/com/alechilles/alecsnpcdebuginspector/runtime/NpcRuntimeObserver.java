package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        records.addAll(sensorObserver.traceRecords(requestId, tick, current, fixtures));
        records.addAll(actionObserver.traceRecords(requestId, tick, current, previous, actionLifecycleTracker));
        Map<String, Object> tamework = current.tameworkMap();
        if (!tamework.isEmpty()) {
            records.add(record(requestId, tick, "tamework", tamework));
            records.addAll(tameworkObserver.traceRecords(requestId, tick, tamework));
        }
        records.addAll(engineHookObserver.traceRecords(requestId, tick, npcId, current, previous, engineHooks));

        NpcRuntimeObservationDiff diff = NpcRuntimeObservationDiff.between(previous, current);
        if (diff.changed()) {
            records.add(NpcRuntimeTraceRecord.of(requestId, tick, "npc-transition")
                    .with("changeCount", diff.changes().size())
                    .with("changes", diff.changes().stream().map(NpcRuntimeObserver::changeMap).toList()));
        }
        return records;
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
}
