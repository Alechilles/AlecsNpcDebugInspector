package com.alechilles.alecsnpcdebuginspector.ui;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Read-only page that renders one NPC debug snapshot.
 */
public final class NpcDebugInspectorPage extends InteractiveCustomUIPage<NpcDebugInspectorPage.PageEventData> {
    public static final String UI_PATH = "NpcDebugInspectorPage.ui";
    private static final String EVENT_ACTION = "Action";
    private static final String ACTION_CLOSE = "Close";
    private static final long REFRESH_INTERVAL_MS = 1000L;

    private final Supplier<NpcDebugSnapshot> snapshotSupplier;
    private volatile boolean dismissed;
    private volatile boolean refreshLoopStarted;

    public NpcDebugInspectorPage(@Nonnull PlayerRef playerRef,
                                 @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.snapshotSupplier = snapshotSupplier;
        this.dismissed = false;
        this.refreshLoopStarted = false;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append(UI_PATH);
        applySnapshot(commandBuilder, resolveSnapshot());
        bindCloseEvent(eventBuilder);
        startRefreshLoop();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PageEventData data) {
        if (data.action == null || data.action.isBlank() || ACTION_CLOSE.equals(data.action)) {
            close();
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        dismissed = true;
    }

    private void bindCloseEvent(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugInspectorCloseButton",
                EventData.of(EVENT_ACTION, ACTION_CLOSE),
                false
        );
    }

    private void applySnapshot(@Nonnull UICommandBuilder commandBuilder, @Nonnull NpcDebugSnapshot snapshot) {
        commandBuilder.set("#NpcDebugInspectorTitle.Text", snapshot.title());
        commandBuilder.set("#NpcDebugInspectorSubtitle.Text", snapshot.subtitle());
        commandBuilder.set("#NpcDebugInspectorDetails.Text", snapshot.details());
    }

    @Nonnull
    private NpcDebugSnapshot resolveSnapshot() {
        if (snapshotSupplier == null) {
            return new NpcDebugSnapshot("NPC Debug Inspector", "Snapshot supplier unavailable", "No data.");
        }
        try {
            NpcDebugSnapshot snapshot = snapshotSupplier.get();
            if (snapshot != null) {
                return snapshot;
            }
        } catch (RuntimeException ignored) {
            // Return fallback below.
        }
        return new NpcDebugSnapshot("NPC Debug Inspector", "Snapshot unavailable", "No data.");
    }

    private void startRefreshLoop() {
        if (refreshLoopStarted) {
            return;
        }
        refreshLoopStarted = true;
        scheduleRefreshTick();
    }

    private void scheduleRefreshTick() {
        CompletableFuture.runAsync(
                this::dispatchRefreshTick,
                CompletableFuture.delayedExecutor(REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS)
        );
    }

    private void dispatchRefreshTick() {
        if (dismissed) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null || store.getExternalData() == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        world.execute(this::runRefreshTickOnWorldThread);
    }

    private void runRefreshTickOnWorldThread() {
        if (dismissed) {
            return;
        }
        sendRefreshUpdate();
        if (!dismissed) {
            scheduleRefreshTick();
        }
    }

    private void sendRefreshUpdate() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        applySnapshot(commandBuilder, resolveSnapshot());
        bindCloseEvent(eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    /** Event payload for the inspector page. */
    public static final class PageEventData {
        public static final BuilderCodec<PageEventData> CODEC = BuilderCodec.builder(
                PageEventData.class,
                PageEventData::new
        )
            .append(
                new KeyedCodec<>(EVENT_ACTION, Codec.STRING),
                (data, value) -> data.action = value,
                data -> data.action
            )
            .add()
            .build();

        private String action;
    }
}
