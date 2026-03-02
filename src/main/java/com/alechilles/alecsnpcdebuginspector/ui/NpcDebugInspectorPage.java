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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
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
    private static final String FIELD_ROW_UI_PATH = "NpcDebugInspectorFieldRow.ui";
    private static final String EVENT_ACTION = "Action";
    private static final String ACTION_CLOSE = "Close";
    private static final String ACTION_TOGGLE_PIN_MODE = "TogglePinMode";
    private static final String ACTION_TOGGLE_FIELD_PREFIX = "TogglePinnedField:";
    private static final long REFRESH_INTERVAL_MS = 1000L;

    private final Supplier<NpcDebugSnapshot> snapshotSupplier;
    @Nullable
    private final UUID targetNpcUuid;
    private NpcDebugSnapshot latestSnapshot;
    private NpcDebugInspectorLine[] inspectorLines;
    private boolean pinModeEnabled;
    private final LinkedHashSet<String> pinnedFieldKeys;
    private volatile boolean dismissed;
    private volatile boolean refreshLoopStarted;

    public NpcDebugInspectorPage(@Nonnull PlayerRef playerRef,
                                 @Nullable UUID targetNpcUuid,
                                 @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.snapshotSupplier = snapshotSupplier;
        this.targetNpcUuid = targetNpcUuid;
        this.latestSnapshot = new NpcDebugSnapshot("NPC Debug Inspector", "", "No data.");
        this.inspectorLines = new NpcDebugInspectorLine[0];
        this.pinModeEnabled = false;
        this.pinnedFieldKeys = new LinkedHashSet<>();
        this.dismissed = false;
        this.refreshLoopStarted = false;
    }

    /**
     * Legacy constructor kept for compatibility where target UUID is unavailable.
     */
    public NpcDebugInspectorPage(@Nonnull PlayerRef playerRef,
                                 @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier) {
        this(playerRef, null, snapshotSupplier);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append(UI_PATH);
        refreshSnapshotData();
        syncPinnedStateFromManager();
        applySnapshot(commandBuilder);
        rebuildFieldRows(commandBuilder, eventBuilder);
        bindGlobalEvents(eventBuilder);
        startRefreshLoop();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PageEventData data) {
        if (data.action == null || data.action.isBlank() || ACTION_CLOSE.equals(data.action)) {
            close();
            return;
        }
        if (ACTION_TOGGLE_PIN_MODE.equals(data.action)) {
            togglePinMode();
            sendRefreshUpdate();
            return;
        }
        if (data.action.startsWith(ACTION_TOGGLE_FIELD_PREFIX)) {
            int index = parseFieldIndex(data.action);
            if (index >= 0) {
                togglePinnedField(index);
                sendRefreshUpdate();
            }
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        dismissed = true;
    }

    private void togglePinMode() {
        if (targetNpcUuid == null) {
            return;
        }
        if (pinModeEnabled) {
            NpcDebugPinnedOverlayManager.unpinNpc(playerRef, targetNpcUuid);
            pinModeEnabled = false;
            pinnedFieldKeys.clear();
        } else {
            NpcDebugPinnedOverlayManager.pinNpc(playerRef, targetNpcUuid, snapshotSupplier);
            pinModeEnabled = true;
            pinnedFieldKeys.clear();
            NpcDebugPinnedOverlayManager.updatePinnedFieldKeys(playerRef, targetNpcUuid, pinnedFieldKeys);
        }
    }

    private int parseFieldIndex(@Nonnull String action) {
        String value = action.substring(ACTION_TOGGLE_FIELD_PREFIX.length()).trim();
        if (value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void togglePinnedField(int index) {
        if (!pinModeEnabled || targetNpcUuid == null || index < 0 || index >= inspectorLines.length) {
            return;
        }
        NpcDebugInspectorLine line = inspectorLines[index];
        if (!line.pinnable || line.key == null) {
            return;
        }
        if (pinnedFieldKeys.contains(line.key)) {
            pinnedFieldKeys.remove(line.key);
        } else {
            pinnedFieldKeys.add(line.key);
        }
        NpcDebugPinnedOverlayManager.updatePinnedFieldKeys(playerRef, targetNpcUuid, pinnedFieldKeys);
    }

    private void bindGlobalEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugInspectorCloseButton",
                EventData.of(EVENT_ACTION, ACTION_CLOSE),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NpcDebugInspectorPinButton",
                EventData.of(EVENT_ACTION, ACTION_TOGGLE_PIN_MODE),
                false
        );
    }

    private void applySnapshot(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set("#NpcDebugInspectorTitle.Text", latestSnapshot.title());
        commandBuilder.set("#NpcDebugInspectorSubtitle.Text", latestSnapshot.subtitle());
        commandBuilder.set("#NpcDebugInspectorPinButton.Text", pinModeEnabled ? "Unpin" : "Pin NPC");
        commandBuilder.set(
                "#NpcDebugInspectorPinHint.Text",
                pinModeEnabled
                        ? "Pinned overlay active. Select fields to include in the separate overlay."
                        : "Click Pin NPC to create a separate overlay you can keep open while playing."
        );
    }

    private void rebuildFieldRows(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#NpcDebugInspectorFieldList");
        for (int i = 0; i < inspectorLines.length; i++) {
            NpcDebugInspectorLine line = inspectorLines[i];
            String rowSelector = "#NpcDebugInspectorFieldList[" + i + "]";
            commandBuilder.append("#NpcDebugInspectorFieldList", FIELD_ROW_UI_PATH);
            commandBuilder.set(rowSelector + " #FieldText.Text", line.displayText);

            boolean showCheck = pinModeEnabled && line.pinnable && line.key != null;
            commandBuilder.set(rowSelector + " #FieldCheck.Visible", showCheck);
            commandBuilder.set(rowSelector + " #FieldCheck.Value", showCheck && pinnedFieldKeys.contains(line.key));

            if (showCheck) {
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        rowSelector + " #FieldCheck",
                        EventData.of(EVENT_ACTION, ACTION_TOGGLE_FIELD_PREFIX + i),
                        false
                );
            }
        }
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

    private void refreshSnapshotData() {
        latestSnapshot = resolveSnapshot();
        inspectorLines = NpcDebugInspectorLineParser.parse(latestSnapshot.details());
        prunePinnedKeys();
    }

    private void syncPinnedStateFromManager() {
        pinnedFieldKeys.clear();
        if (targetNpcUuid == null) {
            pinModeEnabled = false;
            return;
        }
        pinModeEnabled = NpcDebugPinnedOverlayManager.isPinnedToNpc(playerRef, targetNpcUuid);
        if (!pinModeEnabled) {
            return;
        }
        Set<String> persisted = NpcDebugPinnedOverlayManager.getPinnedFieldKeys(playerRef, targetNpcUuid);
        pinnedFieldKeys.addAll(persisted);
    }

    private void prunePinnedKeys() {
        if (pinnedFieldKeys.isEmpty()) {
            return;
        }
        Set<String> availableKeys = new LinkedHashSet<>();
        for (NpcDebugInspectorLine line : inspectorLines) {
            if (line.pinnable && line.key != null) {
                availableKeys.add(line.key);
            }
        }
        pinnedFieldKeys.retainAll(availableKeys);
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
        refreshSnapshotData();
        syncPinnedStateFromManager();

        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        applySnapshot(commandBuilder);
        rebuildFieldRows(commandBuilder, eventBuilder);
        bindGlobalEvents(eventBuilder);
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
