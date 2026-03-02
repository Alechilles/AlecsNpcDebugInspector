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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private NpcDebugSnapshot latestSnapshot;
    private InspectorLine[] inspectorLines;
    private boolean pinModeEnabled;
    private final LinkedHashSet<String> pinnedFieldKeys;
    private volatile boolean dismissed;
    private volatile boolean refreshLoopStarted;

    public NpcDebugInspectorPage(@Nonnull PlayerRef playerRef,
                                 @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.snapshotSupplier = snapshotSupplier;
        this.latestSnapshot = new NpcDebugSnapshot("NPC Debug Inspector", "", "No data.");
        this.inspectorLines = new InspectorLine[0];
        this.pinModeEnabled = false;
        this.pinnedFieldKeys = new LinkedHashSet<>();
        this.dismissed = false;
        this.refreshLoopStarted = false;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append(UI_PATH);
        refreshSnapshotData();
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
        pinModeEnabled = !pinModeEnabled;
        if (!pinModeEnabled) {
            pinnedFieldKeys.clear();
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
        if (!pinModeEnabled || index < 0 || index >= inspectorLines.length) {
            return;
        }
        InspectorLine line = inspectorLines[index];
        if (!line.pinnable || line.key == null) {
            return;
        }
        if (pinnedFieldKeys.contains(line.key)) {
            pinnedFieldKeys.remove(line.key);
        } else {
            pinnedFieldKeys.add(line.key);
        }
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
                        ? "Pin mode active: check fields to include in the pinned panel."
                        : "Click Pin NPC to enable per-field pinning."
        );
        commandBuilder.set("#NpcDebugInspectorPinnedPanel.Visible", pinModeEnabled);
        commandBuilder.set("#NpcDebugPinnedCount.Text", "Pinned fields: " + pinnedFieldKeys.size());
        commandBuilder.set("#NpcDebugPinnedText.Text", buildPinnedPanelText());
    }

    @Nonnull
    private String buildPinnedPanelText() {
        if (!pinModeEnabled) {
            return "";
        }
        if (pinnedFieldKeys.isEmpty()) {
            return "No pinned fields yet.\n\nSelect checkboxes in the inspector list to track values here.";
        }

        Map<String, InspectorLine> byKey = new HashMap<>();
        for (InspectorLine line : inspectorLines) {
            if (line.pinnable && line.key != null) {
                byKey.put(line.key, line);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String key : pinnedFieldKeys) {
            InspectorLine line = byKey.get(key);
            if (line == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("- ").append(line.pinnedText);
        }
        if (sb.length() == 0) {
            return "Pinned fields are temporarily unavailable in the current snapshot.";
        }
        return sb.toString();
    }

    private void rebuildFieldRows(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#NpcDebugInspectorFieldList");
        for (int i = 0; i < inspectorLines.length; i++) {
            InspectorLine line = inspectorLines[i];
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
        inspectorLines = parseInspectorLines(latestSnapshot.details());
        prunePinnedKeys();
    }

    private void prunePinnedKeys() {
        if (pinnedFieldKeys.isEmpty()) {
            return;
        }
        Set<String> availableKeys = new HashSet<>();
        for (InspectorLine line : inspectorLines) {
            if (line.pinnable && line.key != null) {
                availableKeys.add(line.key);
            }
        }
        pinnedFieldKeys.retainAll(availableKeys);
    }

    @Nonnull
    private InspectorLine[] parseInspectorLines(@Nullable String details) {
        if (details == null || details.isBlank()) {
            return new InspectorLine[] {
                    new InspectorLine("No inspector lines available.", null, false, "No data")
            };
        }

        String currentSection = "General";
        List<InspectorLine> lines = new ArrayList<>();
        Map<String, Integer> duplicateCounter = new HashMap<>();
        String[] rawLines = details.split("\\R");
        for (String raw : rawLines) {
            if (raw == null) {
                continue;
            }
            String line = raw.stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("=== ") && line.endsWith(" ===")) {
                currentSection = line.substring(4, Math.max(4, line.length() - 4)).trim();
                String sectionText = currentSection.isBlank() ? "Section" : currentSection;
                lines.add(new InspectorLine("=== " + sectionText + " ===", null, false, sectionText));
                continue;
            }

            String normalized = line;
            if (line.startsWith(">> ")) {
                normalized = line.substring(3);
            } else if (line.startsWith("- ")) {
                normalized = line.substring(2);
            }

            int valueSeparator = normalized.indexOf(": ");
            if (valueSeparator <= 0) {
                lines.add(new InspectorLine(line, null, false, currentSection));
                continue;
            }

            String label = normalized.substring(0, valueSeparator).trim();
            String value = normalized.substring(valueSeparator + 2).trim();
            String baseKey = currentSection + "|" + label;
            int seen = duplicateCounter.merge(baseKey, 1, Integer::sum);
            String key = seen == 1 ? baseKey : baseKey + "#" + seen;
            String pinnedText = currentSection + " | " + label + ": " + value;
            lines.add(new InspectorLine(line, key, true, pinnedText));
        }

        if (lines.isEmpty()) {
            lines.add(new InspectorLine("No inspector lines available.", null, false, "No data"));
        }
        return lines.toArray(new InspectorLine[0]);
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

        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        applySnapshot(commandBuilder);
        rebuildFieldRows(commandBuilder, eventBuilder);
        bindGlobalEvents(eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private static final class InspectorLine {
        private final String displayText;
        @Nullable
        private final String key;
        private final boolean pinnable;
        private final String pinnedText;

        private InspectorLine(@Nonnull String displayText,
                              @Nullable String key,
                              boolean pinnable,
                              @Nonnull String pinnedText) {
            this.displayText = displayText;
            this.key = key;
            this.pinnable = pinnable;
            this.pinnedText = pinnedText;
        }
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
