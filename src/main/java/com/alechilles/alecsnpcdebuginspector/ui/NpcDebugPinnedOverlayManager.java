package com.alechilles.alecsnpcdebuginspector.ui;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
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
    private static final long REFRESH_INTERVAL_MS = 1000L;
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
            CompletableFuture.runAsync(
                    this::dispatchTick,
                    CompletableFuture.delayedExecutor(REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS)
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
            String body = resolvePinnedBody(lines);
            String subtitle = "NPC: " + npcUuid + " | Pinned: " + countPinned(lines);
            hud.setContent("NPC Debug Pinned Overlay", subtitle, body);
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
        private String resolvePinnedBody(@Nonnull NpcDebugInspectorLine[] lines) {
            Set<String> keys = getPinnedFieldKeys();
            if (keys.isEmpty()) {
                return "No pinned fields selected.\n\nOpen NPC inspector and check fields to pin.";
            }

            List<String> entries = new ArrayList<>();
            for (NpcDebugInspectorLine line : lines) {
                if (!line.pinnable || line.key == null) {
                    continue;
                }
                if (!keys.contains(line.key)) {
                    continue;
                }
                entries.add("- " + line.pinnedText);
                if (entries.size() >= MAX_PINNED_LINES) {
                    break;
                }
            }
            if (entries.isEmpty()) {
                return "Pinned fields are currently unavailable for this NPC snapshot.";
            }
            return String.join("\n", entries);
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
