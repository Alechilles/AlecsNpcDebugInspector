package com.alechilles.alecsnpcdebuginspector.ui;

import com.alechilles.alecsnpcdebuginspector.debug.NpcDebugSnapshot;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Read-only page that renders one NPC debug snapshot.
 */
public final class NpcDebugInspectorPage extends InteractiveCustomUIPage<NpcDebugInspectorPage.PageEventData> {
    public static final String UI_PATH = "NpcDebugInspectorPage.ui";
    private static final String SECTION_HEADER_UI_PATH = "NpcDebugInspectorSectionHeaderRow.ui";
    private static final String FIELD_ROW_UI_PATH = "NpcDebugInspectorFieldRow.ui";

    private static final String EVENT_ACTION = "Action";
    private static final String ACTION_CLOSE = "Close";
    private static final String ACTION_TOGGLE_PIN_MODE = "TogglePinMode";
    private static final String ACTION_TOGGLE_FIELD_PREFIX = "TogglePinnedField:";
    private static final String ACTION_TOGGLE_SECTION_PREFIX = "ToggleSection:";
    private static final String ACTION_MOVE_SECTION_UP_PREFIX = "MoveSectionUp:";
    private static final String ACTION_MOVE_SECTION_DOWN_PREFIX = "MoveSectionDown:";
    private static final String ACTION_REORDER_INTERACTION = "ReorderInteraction";
    private static final String ACTION_REORDER_SECTION = "ReorderSection";
    private static final String ACTION_REFRESH_RATE_CHANGED = "RefreshRateChanged";
    private static final String EVENT_REFRESH_INTERVAL_MS = "RefreshIntervalMs";

    private static final String OVERVIEW_SECTION_ID = "section.overview";
    private static final long REFRESH_SUPPRESS_AFTER_REORDER_MS = 4000L;
    private static final Pattern BRACKET_INDEX_PATTERN = Pattern.compile("\\[(\\d+)]");
    private static final String[] FROM_INDEX_KEYS = {
            "FromIndex",
            "OldIndex",
            "SourceIndex",
            "DragIndex",
            "DraggedIndex",
            "StartIndex",
            "Index",
            "From"
    };
    private static final String[] TO_INDEX_KEYS = {
            "ToIndex",
            "NewIndex",
            "TargetIndex",
            "DropIndex",
            "DroppedIndex",
            "EndIndex",
            "To"
    };

    private final Supplier<NpcDebugSnapshot> snapshotSupplier;
    @Nullable
    private final UUID targetNpcUuid;

    private NpcDebugSnapshot latestSnapshot;
    private final Map<String, InspectorSection> sectionsById;
    private final ArrayList<String> sectionOrder;
    private final LinkedHashSet<String> collapsedSectionIds;
    private final LinkedHashSet<String> pinnedFieldKeys;
    private final ArrayList<String> renderedFieldKeys;
    private boolean pinModeEnabled;
    private boolean sectionStateInitialized;
    private volatile boolean dismissed;
    private volatile boolean refreshLoopStarted;
    private volatile long refreshSuppressedUntilMs;

    public NpcDebugInspectorPage(@Nonnull PlayerRef playerRef,
                                 @Nullable UUID targetNpcUuid,
                                 @Nonnull Supplier<NpcDebugSnapshot> snapshotSupplier) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.snapshotSupplier = snapshotSupplier;
        this.targetNpcUuid = targetNpcUuid;
        this.latestSnapshot = new NpcDebugSnapshot("NPC Debug Inspector", "", "No data.");
        this.sectionsById = new LinkedHashMap<>();
        this.sectionOrder = new ArrayList<>();
        this.collapsedSectionIds = new LinkedHashSet<>();
        this.pinnedFieldKeys = new LinkedHashSet<>();
        this.renderedFieldKeys = new ArrayList<>();
        this.pinModeEnabled = false;
        this.sectionStateInitialized = false;
        this.dismissed = false;
        this.refreshLoopStarted = false;
        this.refreshSuppressedUntilMs = 0L;
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
        prunePinnedKeys();
        applySnapshot(commandBuilder);
        rebuildRows(commandBuilder, eventBuilder);
        bindGlobalEvents(eventBuilder);
        syncPinnedSectionOrderToOverlay();
        startRefreshLoop();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull String rawData) {
        Map<String, String> rawEventData = decodeRawEventData(rawData);
        if (ACTION_REORDER_SECTION.equals(rawEventData.get(EVENT_ACTION))) {
            suppressRefreshTemporarily();
            if (applySectionReorder(rawEventData)) {
                sendRefreshUpdate();
            }
            return;
        }
        super.handleDataEvent(ref, store, rawData);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PageEventData data) {
        if (data.action == null || data.action.isBlank()) {
            return;
        }
        if (ACTION_CLOSE.equals(data.action)) {
            close();
            return;
        }
        if (ACTION_TOGGLE_PIN_MODE.equals(data.action)) {
            togglePinMode();
            sendRefreshUpdate();
            return;
        }
        if (ACTION_REORDER_INTERACTION.equals(data.action)) {
            suppressRefreshTemporarily();
            return;
        }
        if (ACTION_REFRESH_RATE_CHANGED.equals(data.action)) {
            applyRefreshIntervalFromEvent(data.refreshIntervalMs);
            sendRefreshUpdate();
            return;
        }
        if (data.action.startsWith(ACTION_MOVE_SECTION_UP_PREFIX)) {
            String sectionId = parseActionSuffix(data.action, ACTION_MOVE_SECTION_UP_PREFIX);
            if (sectionId != null && moveSectionByOffset(sectionId, -1)) {
                suppressRefreshTemporarily();
                sendRefreshUpdate();
            }
            return;
        }
        if (data.action.startsWith(ACTION_MOVE_SECTION_DOWN_PREFIX)) {
            String sectionId = parseActionSuffix(data.action, ACTION_MOVE_SECTION_DOWN_PREFIX);
            if (sectionId != null && moveSectionByOffset(sectionId, 1)) {
                suppressRefreshTemporarily();
                sendRefreshUpdate();
            }
            return;
        }
        if (data.action.startsWith(ACTION_TOGGLE_SECTION_PREFIX)) {
            String sectionId = parseActionSuffix(data.action, ACTION_TOGGLE_SECTION_PREFIX);
            if (sectionId != null) {
                toggleSectionCollapsed(sectionId);
                sendRefreshUpdate();
            }
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
            syncPinnedSectionOrderToOverlay();
        }
    }

    private void applyRefreshIntervalFromEvent(@Nullable String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        try {
            double parsed = Double.parseDouble(rawValue.trim());
            NpcDebugUiRefreshSettings.setFromUiValue(playerRef, parsed);
        } catch (NumberFormatException ignored) {
            // Ignore malformed UI value.
        }
    }

    @Nullable
    private String parseActionSuffix(@Nonnull String action, @Nonnull String prefix) {
        String raw = action.substring(prefix.length()).trim();
        if (raw.isBlank()) {
            return null;
        }
        return raw;
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
        if (!pinModeEnabled || targetNpcUuid == null || index < 0 || index >= renderedFieldKeys.size()) {
            return;
        }
        String key = renderedFieldKeys.get(index);
        if (key == null || key.isBlank()) {
            return;
        }
        if (pinnedFieldKeys.contains(key)) {
            pinnedFieldKeys.remove(key);
        } else {
            pinnedFieldKeys.add(key);
        }
        NpcDebugPinnedOverlayManager.updatePinnedFieldKeys(playerRef, targetNpcUuid, pinnedFieldKeys);
    }

    private void toggleSectionCollapsed(@Nonnull String sectionId) {
        if (!sectionsById.containsKey(sectionId)) {
            return;
        }
        if (collapsedSectionIds.contains(sectionId)) {
            collapsedSectionIds.remove(sectionId);
        } else {
            collapsedSectionIds.add(sectionId);
        }
    }

    @Nonnull
    private Map<String, String> decodeRawEventData(@Nullable String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return Map.of();
        }
        try {
            return MapCodec.STRING_HASH_MAP_CODEC.decodeJson(
                    new RawJsonReader(rawData.toCharArray()),
                    ExtraInfo.THREAD_LOCAL.get()
            );
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }

    private boolean applySectionReorder(@Nonnull Map<String, String> rawEventData) {
        Integer fromIndex = findEventIndex(rawEventData, FROM_INDEX_KEYS);
        Integer toIndex = findEventIndex(rawEventData, TO_INDEX_KEYS);

        if (fromIndex == null || toIndex == null) {
            int[] fallbackPair = parseFallbackIndexPair(rawEventData);
            if (fromIndex == null && fallbackPair[0] >= 0) {
                fromIndex = fallbackPair[0];
            }
            if (toIndex == null && fallbackPair[1] >= 0) {
                toIndex = fallbackPair[1];
            }
        }
        if (fromIndex == null || toIndex == null) {
            return false;
        }
        return moveSection(fromIndex, toIndex);
    }

    @Nullable
    private Integer findEventIndex(@Nonnull Map<String, String> rawEventData, @Nonnull String[] candidateKeys) {
        for (String candidateKey : candidateKeys) {
            for (Map.Entry<String, String> entry : rawEventData.entrySet()) {
                if (entry.getKey() == null || !entry.getKey().equalsIgnoreCase(candidateKey)) {
                    continue;
                }
                Integer parsed = parseIndexValue(entry.getValue());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private int[] parseFallbackIndexPair(@Nonnull Map<String, String> rawEventData) {
        int first = -1;
        int second = -1;
        for (String value : rawEventData.values()) {
            if (value == null || !value.contains("NpcDebugInspectorFieldList[")) {
                continue;
            }
            Integer parsed = parseIndexValue(value);
            if (parsed == null) {
                continue;
            }
            if (first < 0) {
                first = parsed;
                continue;
            }
            second = parsed;
            break;
        }
        return new int[] { first, second };
    }

    @Nullable
    private Integer parseIndexValue(@Nullable String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = rawValue.trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // Fallback to selector-style parsing below.
        }

        Matcher matcher = BRACKET_INDEX_PATTERN.matcher(value);
        Integer lastIndex = null;
        while (matcher.find()) {
            try {
                lastIndex = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // Ignore malformed match and continue scanning.
            }
        }
        return lastIndex;
    }

    private boolean moveSection(int fromIndex, int toIndex) {
        if (sectionOrder.size() < 2) {
            return false;
        }
        if (fromIndex < 0 || fromIndex >= sectionOrder.size()) {
            return false;
        }
        int boundedToIndex = Math.max(0, Math.min(toIndex, sectionOrder.size() - 1));
        if (fromIndex == boundedToIndex) {
            return false;
        }

        String removed = sectionOrder.remove(fromIndex);
        int insertIndex = Math.max(0, Math.min(boundedToIndex, sectionOrder.size()));
        sectionOrder.add(insertIndex, removed);
        return true;
    }

    private boolean moveSectionByOffset(@Nonnull String sectionId, int offset) {
        int fromIndex = sectionOrder.indexOf(sectionId);
        if (fromIndex < 0) {
            return false;
        }
        return moveSection(fromIndex, fromIndex + offset);
    }

    private void suppressRefreshTemporarily() {
        refreshSuppressedUntilMs = System.currentTimeMillis() + REFRESH_SUPPRESS_AFTER_REORDER_MS;
    }

    private boolean isRefreshSuppressed() {
        return System.currentTimeMillis() < refreshSuppressedUntilMs;
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
        EventData refreshEventData = new EventData()
                .append(EVENT_ACTION, ACTION_REFRESH_RATE_CHANGED)
                .append(EVENT_REFRESH_INTERVAL_MS, "#NpcDebugInspectorRefreshSlider.Value");
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#NpcDebugInspectorRefreshSlider",
                refreshEventData,
                false
        );
    }

    private void applySnapshot(@Nonnull UICommandBuilder commandBuilder) {
        long refreshIntervalMs = NpcDebugUiRefreshSettings.getIntervalMs(playerRef);
        commandBuilder.set("#NpcDebugInspectorTitle.Text", latestSnapshot.title());
        commandBuilder.set("#NpcDebugInspectorSubtitle.Text", compactSubtitle(latestSnapshot.subtitle()));
        commandBuilder.set("#NpcDebugInspectorPinButton.Text", pinModeEnabled ? "Unpin" : "Pin NPC");
        commandBuilder.set("#NpcDebugInspectorRefreshLabel.Text", NpcDebugUiRefreshSettings.formatIntervalLabel(refreshIntervalMs));
        commandBuilder.set("#NpcDebugInspectorRefreshSlider.Value", NpcDebugUiRefreshSettings.toUiValue(refreshIntervalMs));
        commandBuilder.set(
                "#NpcDebugInspectorPinHint.Text",
                pinModeEnabled
                        ? "Pinned overlay active. Select field checkboxes. Use section arrows to collapse and drag or move buttons to reorder."
                        : "Pin NPC to create a separate overlay. Use section arrows to collapse and drag or move buttons to reorder."
        );
    }

    @Nonnull
    private String compactSubtitle(@Nullable String subtitle) {
        if (subtitle == null || subtitle.isBlank()) {
            return "";
        }
        int markerIndex = subtitle.indexOf("| Changed lines");
        if (markerIndex <= 0) {
            return subtitle;
        }
        return subtitle.substring(0, markerIndex).trim();
    }

    private void rebuildRows(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#NpcDebugInspectorFieldList");
        renderedFieldKeys.clear();

        int sectionIndex = 0;
        for (String sectionId : sectionOrder) {
            InspectorSection section = sectionsById.get(sectionId);
            if (section == null) {
                continue;
            }

            String sectionSelector = "#NpcDebugInspectorFieldList[" + sectionIndex + "]";
            sectionIndex++;
            commandBuilder.append("#NpcDebugInspectorFieldList", SECTION_HEADER_UI_PATH);
            boolean collapsed = collapsedSectionIds.contains(section.id);
            commandBuilder.set(sectionSelector + " #SectionToggleExpandedIcon.Visible", !collapsed);
            commandBuilder.set(sectionSelector + " #SectionToggleCollapsedIcon.Visible", collapsed);
            commandBuilder.set(sectionSelector + " #SectionTitle.Text", section.title.toUpperCase(Locale.ROOT));
            commandBuilder.set(sectionSelector + " #SectionCount.Text", section.fields.length + " fields");
            commandBuilder.clear(sectionSelector + " #SectionFields");
            commandBuilder.set(sectionSelector + " #SectionFields.Visible", !collapsed);
            int sectionOrderIndex = sectionOrder.indexOf(section.id);
            commandBuilder.set(sectionSelector + " #SectionMoveUpButton.Visible", sectionOrderIndex > 0);
            commandBuilder.set(sectionSelector + " #SectionMoveDownButton.Visible", sectionOrderIndex >= 0 && sectionOrderIndex < sectionOrder.size() - 1);

            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sectionSelector + " #SectionToggleButton",
                    EventData.of(EVENT_ACTION, ACTION_TOGGLE_SECTION_PREFIX + section.id),
                    false
            );
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sectionSelector + " #SectionMoveUpButton",
                    EventData.of(EVENT_ACTION, ACTION_MOVE_SECTION_UP_PREFIX + section.id),
                    false
            );
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sectionSelector + " #SectionMoveDownButton",
                    EventData.of(EVENT_ACTION, ACTION_MOVE_SECTION_DOWN_PREFIX + section.id),
                    false
            );
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.MouseEntered,
                    sectionSelector + " #SectionToggleButton",
                    EventData.of(EVENT_ACTION, ACTION_REORDER_INTERACTION),
                    false
            );

            if (collapsed) {
                continue;
            }

            int fieldRowIndex = 0;
            for (InspectorField field : section.fields) {
                String fieldSelector = sectionSelector + " #SectionFields[" + fieldRowIndex + "]";
                fieldRowIndex++;
                commandBuilder.append(sectionSelector + " #SectionFields", FIELD_ROW_UI_PATH);
                commandBuilder.set(fieldSelector + " #FieldText.Text", field.displayText);
                commandBuilder.set(fieldSelector + " #FieldChanged.Visible", field.changed);

                boolean showCheck = pinModeEnabled && field.pinnable && field.key != null;
                commandBuilder.set(fieldSelector + " #FieldCheck.Visible", showCheck);
                commandBuilder.set(fieldSelector + " #FieldCheck.Value", showCheck && pinnedFieldKeys.contains(field.key));

                if (showCheck) {
                    int fieldIndex = renderedFieldKeys.size();
                    renderedFieldKeys.add(field.key);
                    eventBuilder.addEventBinding(
                            CustomUIEventBindingType.ValueChanged,
                            fieldSelector + " #FieldCheck",
                            EventData.of(EVENT_ACTION, ACTION_TOGGLE_FIELD_PREFIX + fieldIndex),
                            false
                    );
                }
            }
        }

        EventData reorderEventData = new EventData()
                .append(EVENT_ACTION, ACTION_REORDER_SECTION)
                .append("FromIndex", "#NpcDebugInspectorFieldList.FromIndex")
                .append("ToIndex", "#NpcDebugInspectorFieldList.ToIndex")
                .append("OldIndex", "#NpcDebugInspectorFieldList.OldIndex")
                .append("NewIndex", "#NpcDebugInspectorFieldList.NewIndex")
                .append("DragIndex", "#NpcDebugInspectorFieldList.DragIndex")
                .append("DropIndex", "#NpcDebugInspectorFieldList.DropIndex")
                .append("Index", "#NpcDebugInspectorFieldList.Index");
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ElementReordered,
                "#NpcDebugInspectorFieldList",
                reorderEventData,
                false
        );
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
        NpcDebugInspectorLine[] lines = NpcDebugInspectorLineParser.parse(latestSnapshot.details());
        rebuildSections(lines);
        syncSectionState();
    }

    private void rebuildSections(@Nonnull NpcDebugInspectorLine[] lines) {
        sectionsById.clear();
        List<InspectorSection> sections = new ArrayList<>();
        Map<String, Integer> idCounts = new HashMap<>();

        String currentTitle = null;
        String currentId = null;
        List<InspectorField> currentFields = new ArrayList<>();

        for (NpcDebugInspectorLine line : lines) {
            if (isSectionHeaderLine(line.displayText)) {
                if (currentTitle != null && currentId != null) {
                    sections.add(new InspectorSection(currentId, currentTitle, currentFields.toArray(new InspectorField[0])));
                }
                currentTitle = extractSectionTitle(line.displayText);
                currentId = buildSectionId(currentTitle, idCounts);
                currentFields = new ArrayList<>();
                continue;
            }

            if (currentTitle == null) {
                currentTitle = "Overview";
                currentId = OVERVIEW_SECTION_ID;
                idCounts.put(currentId, 1);
            }
            InspectorField field = toInspectorField(line);
            if (field != null) {
                currentFields.add(field);
            }
        }

        if (currentTitle != null && currentId != null) {
            sections.add(new InspectorSection(currentId, currentTitle, currentFields.toArray(new InspectorField[0])));
        }

        if (sections.isEmpty()) {
            sections.add(new InspectorSection(
                    OVERVIEW_SECTION_ID,
                    "Overview",
                    new InspectorField[] {
                            new InspectorField(null, "No inspector fields available.", false, false)
                    }
            ));
        }

        for (InspectorSection section : sections) {
            sectionsById.put(section.id, section);
        }
    }

    private void syncSectionState() {
        List<String> snapshotOrder = new ArrayList<>(sectionsById.size());
        for (InspectorSection section : sectionsById.values()) {
            snapshotOrder.add(section.id);
        }

        if (!sectionStateInitialized) {
            sectionOrder.clear();
            sectionOrder.addAll(snapshotOrder);
            collapsedSectionIds.clear();
            for (String id : sectionOrder) {
                if (!OVERVIEW_SECTION_ID.equals(id)) {
                    collapsedSectionIds.add(id);
                }
            }
            sectionStateInitialized = true;
            return;
        }

        sectionOrder.removeIf(id -> !sectionsById.containsKey(id));
        for (String id : snapshotOrder) {
            if (!sectionOrder.contains(id)) {
                sectionOrder.add(id);
            }
        }
        collapsedSectionIds.removeIf(id -> !sectionsById.containsKey(id));
    }

    private boolean isSectionHeaderLine(@Nullable String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith("=== ") && value.endsWith(" ===");
    }

    @Nonnull
    private String extractSectionTitle(@Nonnull String sectionLine) {
        String trimmed = sectionLine.substring(4, Math.max(4, sectionLine.length() - 4)).trim();
        return trimmed.isBlank() ? "Section" : trimmed;
    }

    @Nonnull
    private String buildSectionId(@Nonnull String title, @Nonnull Map<String, Integer> idCounts) {
        String normalized = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".");
        normalized = normalized.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        String baseId = normalized.isBlank() ? "section.unknown" : "section." + normalized;
        int seen = idCounts.merge(baseId, 1, Integer::sum);
        return seen == 1 ? baseId : baseId + "." + seen;
    }

    @Nullable
    private InspectorField toInspectorField(@Nonnull NpcDebugInspectorLine line) {
        String raw = line.displayText;
        if (raw == null || raw.isBlank()) {
            return null;
        }

        boolean changed = raw.startsWith(">> ");
        String value = raw;
        if (value.startsWith(">> ")) {
            value = value.substring(3);
        } else if (value.startsWith("- ")) {
            value = value.substring(2);
        }
        value = value.trim();
        if (value.isBlank()) {
            return null;
        }

        boolean pinnable = line.pinnable && line.key != null;
        return new InspectorField(line.key, value, changed, pinnable);
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
        for (InspectorSection section : sectionsById.values()) {
            for (InspectorField field : section.fields) {
                if (field.pinnable && field.key != null) {
                    availableKeys.add(field.key);
                }
            }
        }
        pinnedFieldKeys.retainAll(availableKeys);
        if (pinModeEnabled && targetNpcUuid != null) {
            NpcDebugPinnedOverlayManager.updatePinnedFieldKeys(playerRef, targetNpcUuid, pinnedFieldKeys);
        }
    }

    private void startRefreshLoop() {
        if (refreshLoopStarted) {
            return;
        }
        refreshLoopStarted = true;
        scheduleRefreshTick();
    }

    private void scheduleRefreshTick() {
        long refreshIntervalMs = NpcDebugUiRefreshSettings.getIntervalMs(playerRef);
        CompletableFuture.runAsync(
                this::dispatchRefreshTick,
                CompletableFuture.delayedExecutor(refreshIntervalMs, TimeUnit.MILLISECONDS)
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
        if (!isRefreshSuppressed()) {
            sendRefreshUpdate();
        }
        if (!dismissed) {
            scheduleRefreshTick();
        }
    }

    private void sendRefreshUpdate() {
        refreshSnapshotData();
        syncPinnedStateFromManager();
        prunePinnedKeys();

        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        applySnapshot(commandBuilder);
        rebuildRows(commandBuilder, eventBuilder);
        bindGlobalEvents(eventBuilder);
        syncPinnedSectionOrderToOverlay();
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void syncPinnedSectionOrderToOverlay() {
        if (!pinModeEnabled || targetNpcUuid == null) {
            return;
        }
        List<String> orderedTitles = new ArrayList<>(sectionOrder.size());
        for (String sectionId : sectionOrder) {
            InspectorSection section = sectionsById.get(sectionId);
            if (section == null || section.title == null || section.title.isBlank()) {
                continue;
            }
            orderedTitles.add(section.title);
        }
        NpcDebugPinnedOverlayManager.updatePinnedSectionOrder(playerRef, targetNpcUuid, orderedTitles);
    }

    private static final class InspectorSection {
        private final String id;
        private final String title;
        private final InspectorField[] fields;

        private InspectorSection(@Nonnull String id,
                                 @Nonnull String title,
                                 @Nonnull InspectorField[] fields) {
            this.id = id;
            this.title = title;
            this.fields = fields;
        }
    }

    private static final class InspectorField {
        @Nullable
        private final String key;
        private final String displayText;
        private final boolean changed;
        private final boolean pinnable;

        private InspectorField(@Nullable String key,
                               @Nonnull String displayText,
                               boolean changed,
                               boolean pinnable) {
            this.key = key;
            this.displayText = displayText;
            this.changed = changed;
            this.pinnable = pinnable;
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
            .append(
                new KeyedCodec<>(EVENT_REFRESH_INTERVAL_MS, Codec.STRING),
                (data, value) -> data.refreshIntervalMs = value,
                data -> data.refreshIntervalMs
            )
            .add()
            .build();

        private String action;
        private String refreshIntervalMs;
    }
}
