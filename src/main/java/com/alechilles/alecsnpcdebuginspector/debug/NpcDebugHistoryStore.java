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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tracks per-NPC field changes and a lightweight recent event timeline.
 */
final class NpcDebugHistoryStore {
    private static final int MAX_EVENTS = 20;
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
        HistoryEntry entry = history.computeIfAbsent(npcUuid, ignored -> new HistoryEntry());
        String previous = entry.fields.put(key, value);
        boolean changed = previous != null && !previous.equals(value);
        if (changed && recordEvent) {
            String event = EVENT_TIME_FORMAT.format(now) + " " + label + ": " + previous + " -> " + value;
            entry.events.addFirst(event);
            while (entry.events.size() > MAX_EVENTS) {
                entry.events.removeLast();
            }
        }
        return new TrackResult(changed, previous);
    }

    @Nonnull
    List<String> events(@Nullable UUID npcUuid) {
        if (npcUuid == null) {
            return List.of();
        }
        HistoryEntry entry = history.get(npcUuid);
        if (entry == null || entry.events.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(entry.events);
    }

    private static final class HistoryEntry {
        private final Map<String, String> fields = new HashMap<>();
        private final Deque<String> events = new ArrayDeque<>();
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

