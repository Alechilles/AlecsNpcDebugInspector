package com.alechilles.alecsnpcdebuginspector.ui;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tracks and updates pinned NPC inspector overlays per player.
 */
public final class NpcDebugPinnedOverlayManager {
    private static final int MAX_PINNED_LINES = 40;
    private static final Map<UUID, OverlaySession> SESSIONS = new ConcurrentHashMap<>();

    private NpcDebugPinnedOverlayManager() {
    }

    public static boolean isPinnedToNpc(@Nonnull PlayerRef playerRef, @Nullable UUID npcUuid) {
        if (npcUuid == null) {
            return false;
        }
        OverlaySession session = SESSIONS.get(playerRef.getUuid());
        return session != null && session.isPinnedTo(npcUuid);
    }

    @Nonnull
    public static Set<String> getPinnedFieldKeys(@Nonnull PlayerRef playerRef, @Nullable UUID npcUuid) {
        if (npcUuid == null) {
            return Set.of();
        }
        OverlaySession session = SESSIONS.get(playerRef.getUuid());
        if (session == null || !session.isPinnedTo(npcUuid)) {
            return Set.of();
        }
        return session.getPinnedFieldKeys();
    }

    public static void pinNpc(@Nonnull PlayerRef playerRef,
                              @Nonnull UUID npcUuid,
                              @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier) {
        OverlaySession session = new OverlaySession(playerRef, npcUuid, snapshotSupplier);
        OverlaySession previous = SESSIONS.put(playerRef.getUuid(), session);
        if (previous != null) {
            previous.stop();
        }
        session.start();
    }

    public static void updatePinnedFieldKeys(@Nonnull PlayerRef playerRef,
                                             @Nonnull UUID npcUuid,
                                             @Nonnull Set<String> fieldKeys) {
        OverlaySession session = SESSIONS.get(playerRef.getUuid());
        if (session == null || !session.isPinnedTo(npcUuid)) {
            return;
        }
        session.setPinnedFieldKeys(fieldKeys);
    }

    public static void updatePinnedSectionOrder(@Nonnull PlayerRef playerRef,
                                                @Nonnull UUID npcUuid,
                                                @Nonnull List<String> sectionOrder) {
        OverlaySession session = SESSIONS.get(playerRef.getUuid());
        if (session == null || !session.isPinnedTo(npcUuid)) {
            return;
        }
        session.setPinnedSectionOrder(sectionOrder);
    }

    public static void unpinNpc(@Nonnull PlayerRef playerRef, @Nullable UUID npcUuid) {
        OverlaySession session = SESSIONS.get(playerRef.getUuid());
        if (session == null) {
            return;
        }
        if (npcUuid != null && !session.isPinnedTo(npcUuid)) {
            return;
        }
        SESSIONS.remove(playerRef.getUuid(), session);
        session.stop();
    }

    private static final class OverlaySession {
        private final PlayerRef playerRef;
        private final UUID npcUuid;
        private final Supplier<NpcDebugSnapshot> snapshotSupplier;
        private final NpcDebugPinnedOverlayHud hud;
        private final LinkedHashSet<String> pinnedFieldKeys;
        private final ArrayList<String> pinnedSectionOrder;
        private volatile boolean active;
        private volatile boolean refreshStarted;
        private volatile boolean hudShown;

        private OverlaySession(@Nonnull PlayerRef playerRef,
                               @Nonnull UUID npcUuid,
                               @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier) {
            this.playerRef = playerRef;
            this.npcUuid = npcUuid;
            this.snapshotSupplier = snapshotSupplier;
            this.hud = new NpcDebugPinnedOverlayHud(playerRef);
            this.pinnedFieldKeys = new LinkedHashSet<>();
            this.pinnedSectionOrder = new ArrayList<>();
            this.active = true;
            this.refreshStarted = false;
            this.hudShown = false;
        }

        private boolean isPinnedTo(@Nonnull UUID targetNpcUuid) {
            return npcUuid.equals(targetNpcUuid);
        }

        @Nonnull
        private Set<String> getPinnedFieldKeys() {
            synchronized (pinnedFieldKeys) {
                return Set.copyOf(pinnedFieldKeys);
            }
        }

        private void setPinnedFieldKeys(@Nonnull Set<String> fieldKeys) {
            synchronized (pinnedFieldKeys) {
                pinnedFieldKeys.clear();
                pinnedFieldKeys.addAll(fieldKeys);
            }
            renderNow();
        }

        private void setPinnedSectionOrder(@Nonnull List<String> sectionOrder) {
            synchronized (pinnedSectionOrder) {
                pinnedSectionOrder.clear();
                for (String section : sectionOrder) {
                    if (section == null || section.isBlank()) {
                        continue;
                    }
                    if (!pinnedSectionOrder.contains(section)) {
                        pinnedSectionOrder.add(section);
                    }
                }
            }
            renderNow();
        }

        @Nonnull
        private List<String> getPinnedSectionOrder() {
            synchronized (pinnedSectionOrder) {
                return List.copyOf(pinnedSectionOrder);
            }
        }

        private void start() {
            if (refreshStarted) {
                return;
            }
            refreshStarted = true;
            renderNow();
            scheduleTick();
        }

        private void stop() {
            active = false;
            hud.clearOverlay();
        }

        private void scheduleTick() {
            long refreshIntervalMs = NpcDebugUiRefreshSettings.getIntervalMs(playerRef);
            CompletableFuture.runAsync(
                    this::dispatchTick,
                    CompletableFuture.delayedExecutor(refreshIntervalMs, TimeUnit.MILLISECONDS)
            );
        }

        private void dispatchTick() {
            if (!active) {
                return;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                stopAndDrop();
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null || store.getExternalData() == null) {
                stopAndDrop();
                return;
            }
            World world = store.getExternalData().getWorld();
            if (world == null) {
                stopAndDrop();
                return;
            }
            world.execute(this::runTickOnWorldThread);
        }

        private void runTickOnWorldThread() {
            if (!active) {
                return;
            }
            renderNow();
            if (active) {
                scheduleTick();
            }
        }

        private void stopAndDrop() {
            active = false;
            hud.clearOverlay();
            SESSIONS.remove(playerRef.getUuid(), this);
        }

        private void renderNow() {
            if (!active) {
                return;
            }

            NpcDebugSnapshot snapshot = resolveSnapshot();
            NpcDebugInspectorLine[] lines = NpcDebugInspectorLineParser.parse(snapshot.details());
            String body = resolvePinnedBody(snapshot.details(), lines);
            String pinnedNpcName = resolvePinnedNpcName(lines);
            String subtitle = "NPC: " + resolvePinnedNpcLabel(lines) + " | Pinned: " + countPinned(lines);
            hud.setContent("Pinned Overlay - " + pinnedNpcName, subtitle, body);
            if (!hudShown) {
                hud.showOverlay();
                hudShown = true;
            } else {
                hud.pushUpdate();
            }
        }

        private int countPinned(@Nonnull NpcDebugInspectorLine[] lines) {
            if (lines.length == 0) {
                return 0;
            }
            Set<String> keys = getPinnedFieldKeys();
            if (keys.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (NpcDebugInspectorLine line : lines) {
                if (!line.pinnable || line.key == null) {
                    continue;
                }
                if (keys.contains(line.key)) {
                    count++;
                }
            }
            return count;
        }

        @Nonnull
        private String resolvePinnedBody(@Nullable String details, @Nonnull NpcDebugInspectorLine[] lines) {
            Set<String> keys = getPinnedFieldKeys();
            if (keys.isEmpty()) {
                return "No pinned fields selected.\n\nOpen NPC inspector and check fields to pin.";
            }

            Map<String, List<String>> entriesBySection = new LinkedHashMap<>();
            int remainingLines = MAX_PINNED_LINES;
            if (keys.contains(NpcDebugInspectorLineParser.EVENTS_LOG_PIN_KEY)) {
                remainingLines = appendPinnedEventsEntries(entriesBySection, details, remainingLines);
            }
            for (NpcDebugInspectorLine line : lines) {
                if (remainingLines <= 0) {
                    break;
                }
                if (!line.pinnable || line.key == null) {
                    continue;
                }
                if (!keys.contains(line.key)) {
                    continue;
                }
                if (NpcDebugInspectorLineParser.EVENTS_LOG_PIN_KEY.equals(line.key)) {
                    continue;
                }
                String sectionName = resolveSectionName(line);
                String normalizedLine = normalizePinnedLineText(line.displayText);
                if (normalizedLine.isBlank()) {
                    continue;
                }
                entriesBySection.computeIfAbsent(sectionName, ignored -> new ArrayList<>()).add("- " + normalizedLine);
                remainingLines--;
            }
            if (entriesBySection.isEmpty()) {
                return "Pinned fields are currently unavailable for this NPC snapshot.";
            }
            return buildSectionedBody(entriesBySection, lines);
        }

        private int appendPinnedEventsEntries(@Nonnull Map<String, List<String>> entriesBySection,
                                              @Nullable String details,
                                              int remainingLines) {
            if (remainingLines <= 0) {
                return 0;
            }
            List<String> lines = extractRecentEventsLines(details);
            List<String> sectionEntries = entriesBySection.computeIfAbsent(
                    NpcDebugInspectorLineParser.RECENT_EVENTS_SECTION,
                    ignored -> new ArrayList<>()
            );
            if (lines.isEmpty()) {
                sectionEntries.add("- No recent tracked changes yet.");
                return Math.max(0, remainingLines - 1);
            }
            int limit = Math.min(remainingLines, lines.size());
            for (int i = 0; i < limit; i++) {
                sectionEntries.add("- " + lines.get(i));
            }
            return remainingLines - limit;
        }

        @Nonnull
        private String buildSectionedBody(@Nonnull Map<String, List<String>> entriesBySection,
                                          @Nonnull NpcDebugInspectorLine[] lines) {
            List<String> sectionOrder = resolveSectionOrder(entriesBySection, lines);
            StringBuilder body = new StringBuilder();
            for (String sectionName : sectionOrder) {
                List<String> entries = entriesBySection.get(sectionName);
                if (entries == null || entries.isEmpty()) {
                    continue;
                }
                if (body.length() > 0) {
                    body.append("\n\n");
                }
                body.append("=== ").append(sectionName).append(" ===").append('\n');
                for (int i = 0; i < entries.size(); i++) {
                    body.append(entries.get(i));
                    if (i < entries.size() - 1) {
                        body.append('\n');
                    }
                }
            }
            if (body.isEmpty()) {
                return "Pinned fields are currently unavailable for this NPC snapshot.";
            }
            return body.toString();
        }

        @Nonnull
        private List<String> resolveSectionOrder(@Nonnull Map<String, List<String>> entriesBySection,
                                                 @Nonnull NpcDebugInspectorLine[] lines) {
            List<String> orderedSections = new ArrayList<>(entriesBySection.size());
            Set<String> seen = new LinkedHashSet<>();

            for (String section : getPinnedSectionOrder()) {
                if (entriesBySection.containsKey(section) && seen.add(section)) {
                    orderedSections.add(section);
                }
            }

            for (NpcDebugInspectorLine line : lines) {
                String raw = line.displayText;
                if (raw == null || !raw.startsWith("=== ") || !raw.endsWith(" ===")) {
                    continue;
                }
                String sectionName = raw.substring(4, Math.max(4, raw.length() - 4)).trim();
                if (sectionName.isBlank()) {
                    continue;
                }
                if (entriesBySection.containsKey(sectionName) && seen.add(sectionName)) {
                    orderedSections.add(sectionName);
                }
            }

            for (String section : entriesBySection.keySet()) {
                if (seen.add(section)) {
                    orderedSections.add(section);
                }
            }
            return orderedSections;
        }

        @Nonnull
        private String resolveSectionName(@Nonnull NpcDebugInspectorLine line) {
            if (line.key != null) {
                int separatorIndex = line.key.indexOf('|');
                if (separatorIndex > 0) {
                    return line.key.substring(0, separatorIndex).trim();
                }
            }
            return "General";
        }

        @Nonnull
        private String normalizePinnedLineText(@Nullable String displayText) {
            if (displayText == null) {
                return "";
            }
            String line = displayText.strip();
            if (line.startsWith(">> ")) {
                line = line.substring(3);
            } else if (line.startsWith("- ")) {
                line = line.substring(2);
            }
            return line.strip();
        }

        @Nonnull
        private String resolvePinnedNpcName(@Nonnull NpcDebugInspectorLine[] lines) {
            return NpcDebugNameHierarchyResolver.resolvePreferredName(lines, "NPC");
        }

        @Nonnull
        private String resolvePinnedNpcLabel(@Nonnull NpcDebugInspectorLine[] lines) {
            return resolvePinnedNpcName(lines) + " (" + npcUuid + ")";
        }

        @Nonnull
        private List<String> extractRecentEventsLines(@Nullable String details) {
            if (details == null || details.isBlank()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            boolean inRecentEvents = false;
            String[] rawLines = details.split("\\R");
            for (String rawLine : rawLines) {
                if (rawLine == null) {
                    continue;
                }
                String line = rawLine.stripTrailing();
                if (line.startsWith("=== ") && line.endsWith(" ===")) {
                    String sectionName = line.substring(4, Math.max(4, line.length() - 4)).trim();
                    if (inRecentEvents) {
                        break;
                    }
                    inRecentEvents = NpcDebugInspectorLineParser.RECENT_EVENTS_SECTION.equalsIgnoreCase(sectionName);
                    continue;
                }
                if (!inRecentEvents || line.isBlank()) {
                    continue;
                }
                if (line.startsWith(">> ")) {
                    line = line.substring(3);
                } else if (line.startsWith("- ")) {
                    line = line.substring(2);
                }
                if (line.startsWith(NpcDebugInspectorLineParser.EVENTS_LOG_LABEL + ":")) {
                    continue;
                }
                lines.add(line);
            }
            return lines;
        }

        @Nonnull
        private NpcDebugSnapshot resolveSnapshot() {
            try {
                NpcDebugSnapshot snapshot = snapshotSupplier.get();
                if (snapshot != null) {
                    return snapshot;
                }
            } catch (RuntimeException ignored) {
                // Fallback below.
            }
            return new NpcDebugSnapshot(
                    "NPC Debug Pinned Overlay",
                    "Snapshot unavailable",
                    "No data."
            );
        }
    }
}
