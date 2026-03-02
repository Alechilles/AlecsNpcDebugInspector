package com.alechilles.alecsnpcdebuginspector.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maintains a persistent per-player highlight rendering loop independent of open UI pages.
 */
public final class NpcDebugHighlightManager {
    private static final long HIGHLIGHT_REFRESH_MS = 100L;
    private static final Map<UUID, HighlightSession> SESSIONS = new ConcurrentHashMap<>();

    private NpcDebugHighlightManager() {
    }

    /**
     * Starts or updates highlight tracking for a player.
     */
    public static void ensureTracking(@Nonnull PlayerRef playerRef, @Nonnull Supplier<Set<UUID>> highlightedNpcSupplier) {
        UUID playerUuid = playerRef.getUuid();
        HighlightSession existing = SESSIONS.get(playerUuid);
        if (existing != null) {
            existing.updateSupplier(highlightedNpcSupplier);
            existing.start();
            return;
        }
        HighlightSession created = new HighlightSession(playerRef, highlightedNpcSupplier);
        HighlightSession previous = SESSIONS.put(playerUuid, created);
        if (previous != null) {
            previous.stop();
        }
        created.start();
    }

    /**
     * Stops highlight tracking for all players.
     */
    public static void stopAll() {
        for (HighlightSession session : SESSIONS.values()) {
            session.stop();
        }
        SESSIONS.clear();
    }

    private static final class HighlightSession {
        private final PlayerRef playerRef;
        private final NpcDebugHighlightVisualizer visualizer;
        private final AtomicLong tickGeneration;
        private volatile Supplier<Set<UUID>> highlightedNpcSupplier;
        private volatile boolean active;
        private volatile boolean started;

        private HighlightSession(@Nonnull PlayerRef playerRef, @Nonnull Supplier<Set<UUID>> highlightedNpcSupplier) {
            this.playerRef = playerRef;
            this.highlightedNpcSupplier = highlightedNpcSupplier;
            this.visualizer = new NpcDebugHighlightVisualizer();
            this.tickGeneration = new AtomicLong();
            this.active = true;
            this.started = false;
        }

        private void updateSupplier(@Nonnull Supplier<Set<UUID>> highlightedNpcSupplier) {
            this.highlightedNpcSupplier = highlightedNpcSupplier;
            this.active = true;
        }

        private void start() {
            if (started) {
                return;
            }
            started = true;
            scheduleTick();
        }

        private void scheduleTick() {
            long generation = tickGeneration.incrementAndGet();
            CompletableFuture.runAsync(
                    () -> dispatchTick(generation),
                    CompletableFuture.delayedExecutor(HIGHLIGHT_REFRESH_MS, TimeUnit.MILLISECONDS)
            );
        }

        private void dispatchTick(long generation) {
            if (!active || generation != tickGeneration.get()) {
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
            world.execute(() -> runTickOnWorldThread(generation));
        }

        private void runTickOnWorldThread(long generation) {
            if (!active || generation != tickGeneration.get()) {
                return;
            }

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                stopAndDrop();
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                stopAndDrop();
                return;
            }

            Set<UUID> highlightedNpcUuids = resolveHighlightedNpcSet();
            if (highlightedNpcUuids.isEmpty()) {
                stopAndDrop();
                return;
            }

            visualizer.render(playerRef, store, highlightedNpcUuids);
            if (active && generation == tickGeneration.get()) {
                scheduleTick();
            }
        }

        @Nonnull
        private Set<UUID> resolveHighlightedNpcSet() {
            Supplier<Set<UUID>> supplier = highlightedNpcSupplier;
            if (supplier == null) {
                return Set.of();
            }
            Set<UUID> value = null;
            try {
                value = supplier.get();
            } catch (RuntimeException ignored) {
                // Treat bad supplier state as empty until the next tracking refresh.
            }
            if (value == null || value.isEmpty()) {
                return Set.of();
            }
            HashSet<UUID> sanitized = new HashSet<>(value.size());
            for (UUID uuid : value) {
                if (uuid != null) {
                    sanitized.add(uuid);
                }
            }
            if (sanitized.isEmpty()) {
                return Set.of();
            }
            return sanitized;
        }

        private void stop() {
            active = false;
            started = false;
            tickGeneration.incrementAndGet();
        }

        private void stopAndDrop() {
            stop();
            SESSIONS.remove(playerRef.getUuid(), this);
        }
    }
}
