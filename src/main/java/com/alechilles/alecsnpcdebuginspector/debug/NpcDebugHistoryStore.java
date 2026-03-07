package com.alechilles.alecsnpcdebuginspector.debug;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tracks per-NPC field changes and a lightweight recent event timeline.
 */
final class NpcDebugHistoryStore {
    private static final int MAX_EVENTS = 250;
    private static final DateTimeFormatter EVENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private final Map<UUID, HistoryEntry> history = new ConcurrentHashMap<>();

    @Nonnull
    TrackResult track(@Nonnull UUID npcUuid,
                      @Nonnull String key,
                      @Nonnull String label,
                      @Nonnull String value,
                      @Nonnull Instant now,
                      boolean recordEvent) {
        return track(npcUuid, key, label, value, now, recordEvent, NpcDebugEventCategory.CORE);
    }

    @Nonnull
    TrackResult track(@Nonnull UUID npcUuid,
                      @Nonnull String key,
                      @Nonnull String label,
                      @Nonnull String value,
                      @Nonnull Instant now,
                      boolean recordEvent,
                      @Nonnull NpcDebugEventCategory eventCategory) {
        HistoryEntry entry = history.computeIfAbsent(npcUuid, ignored -> new HistoryEntry());
        String previous = entry.fields.put(key, value);
        boolean changed = previous != null && !previous.equals(value);
        if (changed && recordEvent) {
            String event = EVENT_TIME_FORMAT.format(now) + " " + label + ": " + previous + " -> " + value;
            pushEvent(entry, event, eventCategory);
        }
        return new TrackResult(changed, previous);
    }

    void recordEvent(@Nonnull UUID npcUuid, @Nonnull Instant now, @Nonnull String description) {
        recordEvent(npcUuid, now, description, NpcDebugEventCategory.CORE);
    }

    void recordEvent(@Nonnull UUID npcUuid,
                     @Nonnull Instant now,
                     @Nonnull String description,
                     @Nonnull NpcDebugEventCategory category) {
        HistoryEntry entry = history.computeIfAbsent(npcUuid, ignored -> new HistoryEntry());
        pushEvent(entry, EVENT_TIME_FORMAT.format(now) + " " + description, category);
    }

    @Nonnull
    List<String> events(@Nullable UUID npcUuid) {
        return events(npcUuid, Set.of(
                NpcDebugEventCategory.CORE,
                NpcDebugEventCategory.TARGETING,
                NpcDebugEventCategory.TIMERS,
                NpcDebugEventCategory.ALARMS,
                NpcDebugEventCategory.NEEDS,
                NpcDebugEventCategory.FLOCK
        ));
    }

    @Nonnull
    List<String> events(@Nullable UUID npcUuid, @Nonnull Set<NpcDebugEventCategory> enabledCategories) {
        if (npcUuid == null) {
            return List.of();
        }
        HistoryEntry entry = history.get(npcUuid);
        if (entry == null || entry.events.isEmpty()) {
            return List.of();
        }
        if (enabledCategories.isEmpty()) {
            return List.of();
        }
        List<String> events = new ArrayList<>(entry.events.size());
        for (EventEntry event : entry.events) {
            if (event == null || !enabledCategories.contains(event.category)) {
                continue;
            }
            events.add(event.text);
        }
        return events;
    }

    int totalEventCount(@Nullable UUID npcUuid) {
        if (npcUuid == null) {
            return 0;
        }
        HistoryEntry entry = history.get(npcUuid);
        if (entry == null || entry.events.isEmpty()) {
            return 0;
        }
        return entry.events.size();
    }

    private static final class HistoryEntry {
        private final Map<String, String> fields = new HashMap<>();
        private final Deque<EventEntry> events = new ArrayDeque<>();
    }

    private static final class EventEntry {
        private final String text;
        private final NpcDebugEventCategory category;

        private EventEntry(@Nonnull String text, @Nonnull NpcDebugEventCategory category) {
            this.text = text;
            this.category = category;
        }
    }

    private void pushEvent(@Nonnull HistoryEntry entry,
                           @Nonnull String event,
                           @Nonnull NpcDebugEventCategory category) {
        entry.events.addFirst(new EventEntry(event, category));
        while (entry.events.size() > MAX_EVENTS) {
            entry.events.removeLast();
        }
    }

    static final class TrackResult {
        final boolean changed;
        @Nullable
        final String previous;

        TrackResult(boolean changed, @Nullable String previous) {
            this.changed = changed;
            this.previous = previous;
        }
    }
}
