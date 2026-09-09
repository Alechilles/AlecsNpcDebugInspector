package com.alechilles.alecsnpcdebuginspector.runtime;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.alechilles.alecsnpcdebuginspector.metrics.NpcWorkMetricSample;
import com.alechilles.alecsnpcdebuginspector.metrics.NpcWorkMetricSnapshot;
import com.alechilles.alecsnpcdebuginspector.metrics.NpcWorkMetricsCollector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Feeds live inspector snapshots through the same observer pipeline used by the runtime harness.
 */
public final class NpcLiveWorkMetricsSampler {
    private static final String LIVE_PANEL_REQUEST_ID = "live-panel";

    private final NpcRuntimeObserver observer;
    private final NpcWorkMetricsCollector collector;
    private final AtomicInteger sampleTick = new AtomicInteger();
    private final Map<String, NpcRuntimeObservedNpc> previousObservedByNpc = new HashMap<>();
    private final Map<String, NpcRuntimeActionObserver.ActionLifecycleTracker> actionTrackersByNpc = new HashMap<>();

    public NpcLiveWorkMetricsSampler(@Nonnull NpcWorkMetricsCollector collector) {
        this(collector, new NpcRuntimeObserver());
    }

    NpcLiveWorkMetricsSampler(@Nonnull NpcWorkMetricsCollector collector,
                              @Nonnull NpcRuntimeObserver observer) {
        this.collector = collector;
        this.observer = observer;
    }

    @Nonnull
    public synchronized NpcWorkMetricSnapshot record(@Nonnull UUID npcUuid,
                                                     @Nonnull NpcDebugSnapshot snapshot) {
        String npcId = npcUuid.toString();
        int tick = sampleTick.incrementAndGet();
        NpcRuntimeObservedNpc current = observer.observe(npcUuid, snapshot);
        NpcRuntimeObservedNpc previous = previousObservedByNpc.get(npcId);
        NpcRuntimeActionObserver.ActionLifecycleTracker actionTracker = actionTrackersByNpc.computeIfAbsent(
                npcId,
                ignored -> new NpcRuntimeActionObserver.ActionLifecycleTracker()
        );
        List<NpcRuntimeTraceRecord> records = observer.traceRecords(
                LIVE_PANEL_REQUEST_ID,
                tick,
                current,
                previous,
                new NpcRuntimeRequest.EngineHooksSpec(false, false, false, false),
                npcId,
                actionTracker
        );
        NpcWorkMetricSample sample = observer.metricSample(npcId, tick, records, current, previous);
        collector.record(sample);
        previousObservedByNpc.put(npcId, current);
        return collector.snapshot(npcId);
    }
}
